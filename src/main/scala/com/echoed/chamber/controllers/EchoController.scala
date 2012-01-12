package com.echoed.chamber.controllers

import org.springframework.stereotype.Controller
import reflect.BeanProperty
import org.slf4j.LoggerFactory
import org.eclipse.jetty.continuation.ContinuationSupport
import javax.servlet.http.{HttpServletRequest, HttpServletResponse}
import com.echoed.util.CookieManager
import org.springframework.web.bind.annotation._
import com.echoed.chamber.domain.EchoClick
import java.net.URLEncoder
import org.springframework.web.servlet.ModelAndView
import com.echoed.chamber.services.echoeduser.{EchoToResponse, EchoedUserServiceLocator, LocateWithIdResponse, GetEchoedUserResponse}
import scalaz._
import Scalaz._
import com.echoed.chamber.domain.views.EchoPossibilityView
import com.echoed.chamber.services.echo.{EchoExistsException, RecordEchoPossibilityResponse, EchoService}


@Controller
@RequestMapping(Array("/echo"))
class EchoController {

    private final val logger = LoggerFactory.getLogger(classOf[EchoController])

    @BeanProperty var echoItView: String = _
    @BeanProperty var buttonView: String = _
    @BeanProperty var loginView: String = _
    @BeanProperty var confirmView: String = _
    @BeanProperty var errorView: String = _
    @BeanProperty var echoConfirm: String = _

    @BeanProperty var echoService: EchoService = _
    @BeanProperty var echoedUserServiceLocator: EchoedUserServiceLocator = _

    @BeanProperty var cookieManager: CookieManager = _

    @RequestMapping(value = Array("/button"), method = Array(RequestMethod.GET))
    def button(
            @CookieValue(value = "echoedUserId", required = false) echoedUserId: String,
            echoPossibilityParameters: EchoPossibilityParameters,
            httpServletResponse: HttpServletResponse) = {
        if (echoedUserId != null) echoPossibilityParameters.echoedUserId = echoedUserId
        echoService.recordEchoPossibility(echoPossibilityParameters.createButtonEchoPossibility)
        new ModelAndView(buttonView)
    }


    @RequestMapping(method = Array(RequestMethod.GET))
    def echo(
            @CookieValue(value = "echoedUserId", required = false) echoedUserId: String,
            echoPossibilityParameters: EchoPossibilityParameters,
            httpServletRequest: HttpServletRequest,
            httpServletResponse: HttpServletResponse) = {

        val continuation = ContinuationSupport.getContinuation(httpServletRequest)

        if (echoedUserId != null) echoPossibilityParameters.echoedUserId = echoedUserId

        def error(e: Throwable) {
            logger.error("Unexpected error encountered echoing %s" format echoPossibilityParameters, e)
            val modelAndView = new ModelAndView(errorView, "errorMessage", e.getMessage)
            continuation.setAttribute("modelAndView", modelAndView)
            continuation.resume
            modelAndView
        }

        def confirmEchoPossibility {
            echoedUserServiceLocator.getEchoedUserServiceWithId(echoPossibilityParameters.echoedUserId).onResult {
                case LocateWithIdResponse(_, Left(e)) =>
                    logger.debug("Error {} finding EchoedUserService for {}", e.getMessage, echoPossibilityParameters)
                    loginToEcho
                case LocateWithIdResponse(_, Right(echoedUserService)) =>
                    echoedUserService.getEchoedUser.onResult {
                        case GetEchoedUserResponse(_, Left(e)) => error(e)
                        case GetEchoedUserResponse(_, Right(echoedUser)) =>
                            val echoPossibility = echoPossibilityParameters.createConfirmEchoPossibility

                            echoService.recordEchoPossibility(echoPossibility).onResult {
                                case RecordEchoPossibilityResponse(_, Left(EchoExistsException(epv, message, _))) =>
                                    logger.debug("Echo possibility already echoed {}", epv.echo)
                                    val modelAndView = new ModelAndView(errorView)
                                    modelAndView.addObject("errorMessage", "This item has already been shared")
                                    modelAndView.addObject("echoPossibilityView", epv)
                                    continuation.setAttribute("modelAndView", modelAndView)
                                    continuation.resume
                                case RecordEchoPossibilityResponse(_, Left(e)) => error(e)
                                case RecordEchoPossibilityResponse(_, Right(epv)) =>
                                    val modelAndView = new ModelAndView(confirmView)
                                    modelAndView.addObject("echoedUser", echoedUser)
                                    modelAndView.addObject("echoPossibility", echoPossibility)
                                    modelAndView.addObject("retailer", epv.retailer)
                                    modelAndView.addObject("retailerSettings", epv.retailerSettings)
                                    modelAndView.addObject(
                                            "facebookAddUrl",
                                            URLEncoder.encode(
                                                    echoPossibility.asUrlParams("http://v1-api.echoed.com/facebook/login/add?redirect=echo?"),
                                                    "UTF-8"))
                                    modelAndView.addObject(
                                            "twitterAddUrl",
                                            URLEncoder.encode(echoPossibility.asUrlParams("echo?"), "UTF-8"))
                                    continuation.setAttribute("modelAndView", modelAndView)
                                    continuation.resume
                            }.onException { case e => error(e) }
                    }.onException { case e => error(e) }
            }.onException { case e => error(e) }
        }

        def loginToEcho {
            echoService.recordEchoPossibility(echoPossibilityParameters.createLoginEchoPossibility).onResult {
                case RecordEchoPossibilityResponse(_, Left(e)) => error(e)
                case RecordEchoPossibilityResponse(_, Right(epv)) =>
                    val modelAndView = new ModelAndView(loginView)
                    modelAndView.addObject(
                        "twitterUrl",
                        URLEncoder.encode(epv.echoPossibility.asUrlParams("echo?"), "UTF-8"))

                    modelAndView.addObject("redirectUrl",
                        URLEncoder.encode("http://v1-api.echoed.com/facebook/login?redirect="
                            + URLEncoder.encode(epv.echoPossibility.asUrlParams("echo?"), "UTF-8"), "UTF-8"))

                    modelAndView.addObject("echoPossibilityView", epv)

                    continuation.setAttribute("modelAndView", modelAndView)
                    continuation.resume
            }.onException { case e => error(e) }
        }


        if (continuation.isExpired) {
            error(RequestExpiredException("We encountered an error echoing your purchase"))
        } else Option(continuation.getAttribute("modelAndView")).getOrElse({
            continuation.suspend(httpServletResponse)

            Option(echoPossibilityParameters.echoedUserId).cata(
                _ => confirmEchoPossibility,
                loginToEcho)

            continuation.undispatch
        })
    }


    @RequestMapping(value = Array("/it"), method = Array(RequestMethod.GET))
    def it(
            @CookieValue(value = "echoedUserId", required = false) echoedUserId: String,
            echoItParameters: EchoItParameters,
            httpServletRequest: HttpServletRequest,
            httpServletResponse: HttpServletResponse) = {

        if (echoedUserId != null) echoItParameters.echoedUserId = echoedUserId

        val continuation = ContinuationSupport.getContinuation(httpServletRequest)


        def error(e: Throwable) = {
            logger.error("Unexpected error echoing {}", echoItParameters, e)
            val modelAndView = new ModelAndView(errorView, "errorMessage", e.getMessage)
            continuation.setAttribute("modelAndView", modelAndView)
            continuation.resume
            modelAndView
        }

        if (continuation.isExpired) {
            error(RequestExpiredException("We encounted an error echoing your purchase"))
        } else Option(continuation.getAttribute("modelAndView")).getOrElse({
            continuation.suspend(httpServletResponse)

            logger.debug("Echoing {}", echoItParameters)
            logger.debug("echoPossibilityId {}", echoItParameters.echoPossibilityId)
            echoedUserServiceLocator.getEchoedUserServiceWithId(echoItParameters.echoedUserId).onComplete(_.value.get.fold(
                e => error(e),
                locateWithIdResponse => locateWithIdResponse match {
                    case LocateWithIdResponse(_, Left(e)) => error(e)
                    case LocateWithIdResponse(_, Right(echoedUserService)) => {
                        echoedUserService.echoTo(echoItParameters.createEchoTo).onComplete(_.value.get.fold(
                            e => error(e),
                            echoToResponse => echoToResponse match {
                                case EchoToResponse(_, Left(e)) => error(e)
                                case EchoToResponse(_, Right(echoFull)) => {
                                    continuation.setAttribute("modelAndView", new ModelAndView(echoItView, "echoFull", echoFull))
                                    continuation.resume
                                }
                            }
                        ))
                    }

                }
            ))

            continuation.undispatch()
        })

    }

    @RequestMapping(value = Array("/{echoId}/{postId}"), method = Array(RequestMethod.GET))
    def echoes(
            @PathVariable(value = "echoId") echoId: String,
            @PathVariable(value = "postId") postId: String,
            @CookieValue(value = "echoedUserId", required = false) echoedUserId: String,
            @RequestHeader(value = "Referer", required = true) referrerUrl: String,
            httpServletRequest: HttpServletRequest,
            httpServletResponse: HttpServletResponse) = {

        val continuation = ContinuationSupport.getContinuation(httpServletRequest)
        if (continuation.isExpired) {
            logger.error("Request expired to record echo click for echo {}", echoId)
            new ModelAndView(errorView)
        } else Option(continuation.getAttribute("modelAndView")).getOrElse({

            continuation.suspend(httpServletResponse)
            logger.debug("Hello?");
            val echoClick = new EchoClick(echoId, echoedUserId, referrerUrl, httpServletRequest.getRemoteAddr)
            echoService.recordEchoClick(echoClick, postId).map { tuple =>
                cookieManager.addCookie(httpServletResponse, "echoClick", tuple._1.id)
                continuation.setAttribute("modelAndView", new ModelAndView("redirect:%s" format tuple._2))
                continuation.resume
            }
            continuation.undispatch()
        })
    }
}


