package shop.service

import org.jooq.DSLContext
import org.jooq.impl.DSL
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import shop.Tables.*
import shop.controller.form.CancelForm
import shop.controller.form.SaveForm
import shop.controller.form.UseForm
import java.math.BigDecimal
import java.util.*

fun guid(): String {
    val uuid = UUID.randomUUID().toString()
    return listOf(
        15 to 4,
        10 to 4,
        1 to 8,
        20 to 4,
        25 to 12
    ).joinToString("") { (s, endIndex) ->
        val startIndex = s - 1
        uuid.substring(startIndex, startIndex + endIndex)
    }
}

enum class PointType {
    SAVE, USE, CANCEL, EXPIRED
}

@Service
@Transactional
class PointService(
    private val dsl: DSLContext
) {
    // user 테이블의 point 갱신
    private fun refreshUserTotalPoint(userId: Long) {
        val totalPoint = dsl
            .select(
                DSL.sum(USER_POINT.POINT)
            )
            .from(USER_POINT)
            .where(
                USER_POINT.USER_ID.eq(userId)
            )
            .fetchSingle()
            .value1()
            .toLong()

        dsl.update(USER)
            .set(USER.POINT, totalPoint.toInt())
            .where(
                USER.ID.eq(userId)
            )
            .execute()
    }

    fun save(form: SaveForm) {
        val (userId, point) = form

        val pointType = PointType.SAVE.name
        val pointId = dsl.insertInto(USER_POINT)
            .set(USER_POINT.USER_ID, userId)
            .set(USER_POINT.TYPE, pointType)
            .set(USER_POINT.POINT, point)
            .set(USER_POINT.EXPIRED_AT, DSL.localDateTimeAdd(DSL.currentLocalDateTime(), 3)) // 3일
            .returningResult(USER_POINT.ID)
            .single()
            .value1()

        val detailId = guid().toByteArray()

        dsl.insertInto(USER_POINT_DETAIL)
            .set(USER_POINT_DETAIL.ID, detailId)
            .set(USER_POINT_DETAIL.USER_POINT_ID, pointId)
            .set(USER_POINT_DETAIL.TYPE, pointType)
            .set(USER_POINT_DETAIL.POINT, point)
            .set(USER_POINT_DETAIL.GROUP_ID, detailId)
            .execute()

        expired(userId)
    }

    fun use(form: UseForm) {
        val (userId, point) = form

        // TODO : 유저가 가지고 있는 총 포인트 보다 사용할 포인트가 적은지 확인

        // 사용가능한 포인트 목록을 조회 한다.
        val pointSumField = DSL.sum(USER_POINT_DETAIL.POINT).`as`("p")
        val availablePoints = dsl
            .select(
                USER_POINT_DETAIL.GROUP_ID,
                pointSumField
            )
            .from(USER_POINT)
            .join(USER_POINT_DETAIL).on(USER_POINT.ID.eq(USER_POINT_DETAIL.USER_POINT_ID))
            .where(
                USER_POINT.USER_ID.eq(userId)
            )
            .groupBy(
                USER_POINT_DETAIL.GROUP_ID
            )
            .having(
                pointSumField.greaterThan(BigDecimal.valueOf(0))
            )
            .orderBy(
                USER_POINT.EXPIRED_AT.asc()
            )
            .fetch {
                it.value1() to it.value2().toLong()
            }

        // 포인트 사용
        val pointType = PointType.USE.name
        val pointId = dsl.insertInto(USER_POINT)
            .set(USER_POINT.USER_ID, userId)
            .set(USER_POINT.TYPE, pointType)
            .set(USER_POINT.POINT, point * -1)
            .returningResult(USER_POINT.ID)
            .single()
            .value1()

        // 포인트 상세 차감 처리
        var tempPoint = point.toLong()
        for (availablePoint in availablePoints) {
            val (groupId, p) = availablePoint

            if (tempPoint > p) {
                tempPoint -= p

                dsl.insertInto(USER_POINT_DETAIL)
                    .set(USER_POINT_DETAIL.ID, guid().toByteArray())
                    .set(USER_POINT_DETAIL.USER_POINT_ID, pointId)
                    .set(USER_POINT_DETAIL.TYPE, pointType)
                    .set(USER_POINT_DETAIL.POINT, (p * -1).toInt())
                    .set(USER_POINT_DETAIL.GROUP_ID, groupId)
                    .execute()
            } else {
                dsl.insertInto(USER_POINT_DETAIL)
                    .set(USER_POINT_DETAIL.ID, guid().toByteArray())
                    .set(USER_POINT_DETAIL.USER_POINT_ID, pointId)
                    .set(USER_POINT_DETAIL.TYPE, pointType)
                    .set(USER_POINT_DETAIL.POINT, (tempPoint * -1).toInt())
                    .set(USER_POINT_DETAIL.GROUP_ID, groupId)
                    .execute()
                break
            }
        }

        expired(userId)
    }

    fun cancel(form: CancelForm) {
        val (userId, pointId) = form

        // 사용한 포인트 조회
        val pointType = PointType.USE.name
        val pointRecord = dsl
            .selectFrom(USER_POINT)
            .where(
                USER_POINT.ID.eq(pointId)
                    .and(USER_POINT.USER_ID.eq(userId))
                    .and(USER_POINT.TYPE.eq(pointType))
            )
            .fetchOne() ?: throw NullPointerException()

        val details = dsl
            .selectFrom(USER_POINT_DETAIL)
            .where(
                USER_POINT_DETAIL.USER_POINT_ID.eq(pointId)
                    .and(USER_POINT_DETAIL.TYPE.eq(pointType))
            )
            .fetch()
            .ifEmpty { throw NullPointerException() }

        // 취소 포인트 추가
        val newPointType = PointType.CANCEL.name
        val newPointId = dsl.insertInto(USER_POINT)
            .set(USER_POINT.USER_ID, userId)
            .set(USER_POINT.TYPE, newPointType)
            .set(USER_POINT.POINT, pointRecord.point * -1)
            .returningResult(USER_POINT.ID)
            .single()
            .value1()

        for (detail in details) {
            dsl.insertInto(USER_POINT_DETAIL)
                .set(USER_POINT_DETAIL.ID, guid().toByteArray())
                .set(USER_POINT_DETAIL.USER_POINT_ID, newPointId)
                .set(USER_POINT_DETAIL.TYPE, newPointType)
                .set(USER_POINT_DETAIL.POINT, detail.point * -1)
                .set(USER_POINT_DETAIL.GROUP_ID, detail.groupId)
                .execute()
        }

        expired(userId)
    }

    fun expired(userId: Long) {
        val pointSumField = DSL.sum(USER_POINT_DETAIL.POINT).`as`("p")
        val expiredPoints = dsl
            .select(
                USER_POINT_DETAIL.GROUP_ID,
                pointSumField
            )
            .from(USER_POINT)
            .join(USER_POINT_DETAIL).on(USER_POINT.ID.eq(USER_POINT_DETAIL.USER_POINT_ID))
            .where(
                USER_POINT.USER_ID.eq(userId)
                    .and(
                        USER_POINT.EXPIRED_AT.lt(DSL.currentLocalDateTime())
                            .or(USER_POINT.EXPIRED_AT.isNull)
                    )
            )
            .groupBy(
                USER_POINT_DETAIL.GROUP_ID
            )
            .having(
                pointSumField.greaterThan(BigDecimal.valueOf(0))
            )
            .fetch {
                it.value1() to it.value2().toLong()
            }

        val pointType = PointType.EXPIRED.name
        for (expiredPoint in expiredPoints) {
            val (groupId, point) = expiredPoint

            val newPointId = dsl.insertInto(USER_POINT)
                .set(USER_POINT.USER_ID, userId)
                .set(USER_POINT.TYPE, pointType)
                .set(USER_POINT.POINT, (point * -1).toInt())
                .returningResult(USER_POINT.ID)
                .single()
                .value1()

            dsl.insertInto(USER_POINT_DETAIL)
                .set(USER_POINT_DETAIL.ID, guid().toByteArray())
                .set(USER_POINT_DETAIL.USER_POINT_ID, newPointId)
                .set(USER_POINT_DETAIL.TYPE, pointType)
                .set(USER_POINT_DETAIL.POINT, (point * -1).toInt())
                .set(USER_POINT_DETAIL.GROUP_ID, groupId)
                .execute()
        }

        refreshUserTotalPoint(userId)
    }
}
