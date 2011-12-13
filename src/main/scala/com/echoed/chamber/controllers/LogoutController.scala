package com.echoed.chamber.controllers

import org.springframework.stereotype.Controller
import javax.servlet.http.{HttpServletResponse, HttpServletRequest}
import org.eclipse.jetty.continuation.ContinuationSupport
import org.springframework.web.bind.annotation._
import org.springframework.web.servlet.ModelAndView
import reflect.BeanProperty
import com.echoed.util.CookieManager
import org.slf4j.LoggerFactory


@Controller
@RequestMapping(Array("/logout"))
class LogoutController {

    private val logger = LoggerFactory.getLogger(classOf[LogoutController])

    @BeanProperty var cookieManager: CookieManager = _

    @RequestMapping(method = Array(RequestMethod.GET))
    def logout(@CookieValue(value = "echoedUserId", required = true) echoedUserId: String,
               httpServletRequest: HttpServletRequest,
               httpServletResponse: HttpServletResponse) = {
        
        logger.debug("Removing Cookie: echoedUserId");
        cookieManager.deleteCookie(httpServletResponse,"echoedUserId");
        new ModelAndView("redirect:http://v1-api.echoed.com/")
        
    }

}