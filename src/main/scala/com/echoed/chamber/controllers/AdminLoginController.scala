package com.echoed.chamber.controllers

import org.springframework.stereotype.Controller
import javax.servlet.http.{HttpServletRequest, HttpServletResponse}
import org.springframework.web.bind.annotation._
import org.springframework.web.servlet.ModelAndView
import com.echoed.chamber.services.adminuser._
import org.springframework.web.context.request.async.DeferredResult
import com.echoed.chamber.domain.AdminUser


@Controller
@RequestMapping(Array("/admin"))
class AdminLoginController extends EchoedController {

    @RequestMapping(Array("/create"))
    @ResponseBody
    def create(
            aucc: AdminUserClientCredentials,
            @RequestParam(value="email", required = true) email:String,
            @RequestParam(value="password", required = true) password:String,
            @RequestParam(value="name", required = true) name: String,
            @RequestParam(value="token", required = true) token: String) = {

        val result = new DeferredResult(ErrorResult.timeout)

        log.debug("Creating Admin Account for {}, {}", name, email)
        assert(token == "J0n1sR3tard3d")

        mp(CreateAdminUser(aucc, new AdminUser(name, email).createPassword(password))).onSuccess {
            case CreateAdminUserResponse(_, Right(adminUser)) => result.set(adminUser)
        }

        result
    }

    @RequestMapping(Array("/login"))
    def login(
            @RequestParam(value="email", required = false) email:String,
            @RequestParam(value="password", required = false) password: String,
            request: HttpServletRequest,
            response: HttpServletResponse) = {

        if (email == null || password == null) {
            new ModelAndView(v.adminLoginView)
        } else {
            val result = new DeferredResult(new ModelAndView(v.adminLoginView))

            mp(Login(email, password)).onSuccess {
                case LoginResponse(_, Right(adminUser)) =>
                    cookieManager.addAdminUserCookie(
                            response,
                            adminUser,
                            request)
                    result.set(new ModelAndView(v.adminDashboardView))
            }

            result
        }
    }
}