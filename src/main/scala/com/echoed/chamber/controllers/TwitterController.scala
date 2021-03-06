package com.echoed.chamber.controllers

import org.springframework.web.bind.annotation.{RequestParam, RequestMapping, RequestMethod}
import org.springframework.stereotype.Controller
import javax.servlet.http.{HttpServletRequest, HttpServletResponse}
import com.echoed.chamber.services.echoeduser._
import java.net.URLEncoder
import org.springframework.web.servlet.ModelAndView
import org.springframework.web.context.request.async.DeferredResult
import scala.Right
import javax.annotation.Nullable
import scalaz._
import Scalaz._
import scala.concurrent.ExecutionContext.Implicits.global


@Controller
@RequestMapping(Array("/twitter"))
class TwitterController extends EchoedController with NetworkController {

    @RequestMapping(method = Array(RequestMethod.GET))
    def twitter(
            @RequestParam(value = "redirect", required = false) redirect: String,
            @Nullable eucc: EchoedUserClientCredentials) = {

        val result = new DeferredResult[ModelAndView](null, new ModelAndView(v.errorView))


        val callbackUrl = "%s/twitter/%s%s" format(
                v.secureSiteUrl,
                Option(eucc).map(_ => "add").getOrElse("login"),
                Option(redirect).map("?redirect=" + URLEncoder.encode(_, "UTF-8")).getOrElse(""))

        log.debug("Twitter Callback Url: {} ", URLEncoder.encode(callbackUrl,"UTF-8"));

        mp(GetTwitterAuthenticationUrl(callbackUrl)).onSuccess {
            case GetTwitterAuthenticationUrlResponse(_, Right(authenticationUrl)) =>
                result.setResult(new ModelAndView("redirect:" + authenticationUrl))
        }

        result
    }


    @RequestMapping(value = Array("/add"), method= Array(RequestMethod.GET))
    def add(@RequestParam("oauth_token") oAuthToken: String,
            @RequestParam("oauth_verifier") oAuthVerifier: String,
            @RequestParam(value = "redirect", required = false) redirect: String,
            eucc: EchoedUserClientCredentials) = {

        log.debug("Add/QueryString: {} ", redirect)
        val redirectView = v.postAddView + redirect

        mp(AddTwitter(eucc, oAuthToken, oAuthVerifier))
        new ModelAndView(redirectView)
    }



    @RequestMapping(value = Array("/login"), method = Array(RequestMethod.GET))
    def login(@RequestParam("oauth_token") authToken: String,
              @RequestParam("oauth_verifier") authVerifier: String,
              @RequestParam(value = "redirect", required = false) redirect: String,
              request: HttpServletRequest,
              response: HttpServletResponse) = {

        val result = new DeferredResult[ModelAndView](null, new ModelAndView(v.errorView))

        val queryString = request.getQueryString

        log.debug("Twitter/Login/QueryString: {}", queryString)
        log.debug("Twitter/Login/Redirect: {}", redirect)

        mp(LoginWithTwitter(authToken, authVerifier)).onSuccess {
            case LoginWithTwitterResponse(_, Right(echoedUser)) =>
                cookieManager.addEchoedUserCookie(
                        response,
                        echoedUser,
                        request)
                val redirectView = "%s?redirect=%s" format(v.postLoginView, Option(redirect).getOrElse(""))
                log.debug("Redirecting to View: {} ", redirectView)
                result.setResult(new ModelAndView(redirectView))
        }

        result
    }

    def makeAuthorizeUrl(postAuthorizeUrl: String, add: Boolean = false, useExtendedPermissions: Boolean = true) =
        "%s/twitter?redirect=%s" format(v.secureSiteUrl, URLEncoder.encode(postAuthorizeUrl, "UTF-8"))

}
