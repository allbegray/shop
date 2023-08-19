package shop.controller

import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import shop.controller.form.CancelForm
import shop.controller.form.SaveForm
import shop.controller.form.UseForm
import shop.service.PointService

@RestController
@RequestMapping("/point")
class PointController(
    private val service: PointService,
) {

    // 포인트 적립
    @PostMapping("/save")
    fun save(@RequestBody form: SaveForm) {
        service.save(form)
    }

    // 포인트 사용
    @PostMapping("/use")
    fun use(@RequestBody form: UseForm) {
        service.use(form)
    }

    @PostMapping("/cancel")
    fun cancel(@RequestBody form: CancelForm) {
        service.cancel(form)
    }
}