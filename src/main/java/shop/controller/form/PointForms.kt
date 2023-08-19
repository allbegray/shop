package shop.controller.form

data class SaveForm(
    val userId: Long,
    val point: Int
)

data class UseForm(
    val userId: Long,
    val point: Int
)

data class CancelForm(
    val userId: Long,
    val pointId: Long
)
