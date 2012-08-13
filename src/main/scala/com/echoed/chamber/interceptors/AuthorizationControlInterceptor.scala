package com.echoed.chamber.interceptors

import javax.servlet.http.{HttpServletResponse, HttpServletRequest}
import org.springframework.web.servlet.{ModelAndView, HandlerInterceptor}
import org.slf4j.LoggerFactory
import scala.reflect.BeanProperty
import com.echoed.chamber.controllers.CookieManager

class AuthorizationControlInterceptor extends HandlerInterceptor {

    val logger = LoggerFactory.getLogger(classOf[AuthorizationControlInterceptor])

    @BeanProperty var cookieManager: CookieManager = _
    @BeanProperty var httpsUrl: String = _

    def preHandle(request: HttpServletRequest, response: HttpServletResponse, handler: Object) = {
        val path = request.getRequestURI

        if (path == null || (!path.startsWith("/partner") && !path.startsWith("/admin"))) {
            true
        } else {
            val isHttps =
                request.getScheme.equals("https") ||
                Option(request.getHeader("X-Scheme")).filter(_.equals("https")).isDefined
            val isPartner = path.startsWith("/partner")
            val isJson = request.getHeader("Accept").contains("application/json")
            if (!isHttps) {
                logger.debug("Not using ssl for protected page {}", path)
                if (isJson) {
                    response.setStatus(401)
                    logger.debug("Sending 401 response for non-ssl json request {}", path)
                } else if (isPartner) {
                    response.sendRedirect("%s/partner/login" format httpsUrl)
                    logger.debug("Redirecting to /partner/login due to non-ssl request {}", path)
                } else {
                    response.sendRedirect("%s/admin/login" format httpsUrl)
                    logger.debug("Redirecting to /admin/login due to non-ssl request {}", path)
                }
                false
            } else if (
                    path.startsWith("/partner/login") ||
                    path.startsWith("/partner/activate") ||
                    path.startsWith("/partner/register") ||
                    path.startsWith("/admin/login") ||
                    path.startsWith("/admin/create")) {
                true
            } else {
                val pu = cookieManager.findPartnerUserCookie(request)
                val au = cookieManager.findAdminUserCookie(request)
                if (isPartner) {
                    if (pu.filter(_.length == 36).isDefined) true
                    else {
                        if (isJson) response.setStatus(401)
                        else response.sendRedirect("%s/partner/login" format httpsUrl)
                        logger.debug("Did not find partner user cookie for protected request {}", path)
                        false
                    }
                } else {
                    if (au.filter(_.length == 36).isDefined) true
                    else {
                        if (isJson) response.setStatus(401)
                        else response.sendRedirect("%s/admin/login" format httpsUrl)
                        logger.debug("Did not find admin user cookie for protected request {}", path)
                        false
                    }
                }
            }
        }
    }

    def postHandle(request: HttpServletRequest, response: HttpServletResponse, handler: Object, modelAndView: ModelAndView) {
        //
    }

    def afterCompletion(request: HttpServletRequest, response: HttpServletResponse, handler: Object, ex: Exception) {
        //
    }
}
