package com.echoed.chamber.controllers.api.user

import org.springframework.stereotype.Controller
import com.echoed.chamber.controllers.{EchoedController, ErrorResult}
import org.springframework.web.bind.annotation._
import org.springframework.web.context.request.async.DeferredResult
import scala.util.control.Exception._
import java.lang.{NumberFormatException => NFE}
import scala.Right
import com.echoed.chamber.domain._
import scala.concurrent.ExecutionContext.Implicits.global
import views.context.PartnerContext
import views.context.PartnerContext
import views.context.PartnerContext
import views.Feed
import com.echoed.chamber.domain.views.context._

import com.echoed.chamber.services.partner._
import com.echoed.chamber.services.partner.RequestPartnerFollowers
import views.Feed
import com.echoed.chamber.services.partner.RequestPartnerContentResponse
import com.echoed.chamber.services.partner.RequestPartnerContent
import com.echoed.chamber.services.partner.PartnerClientCredentials
import com.echoed.chamber.services.echoeduser._
import com.echoed.chamber.services.partner.RequestPartnerFollowers
import views.Feed
import com.echoed.chamber.services.partner.RequestPartnerContentResponse
import com.echoed.chamber.services.partner.RequestPartnerContent
import com.echoed.chamber.services.partner.PartnerClientCredentials
import com.echoed.chamber.services.partner.RequestPartnerFollowersResponse
import com.echoed.chamber.services.partner.RequestPartnerFollowers
import views.Feed
import com.echoed.chamber.services.echoeduser.EchoedUserClientCredentials
import com.echoed.chamber.services.echoeduser.FollowPartner
import com.echoed.chamber.services.partner.RequestPartnerContentResponse
import com.echoed.chamber.services.echoeduser.UnFollowPartner
import com.echoed.chamber.services.partner.RequestPartnerContent
import com.echoed.chamber.services.echoeduser.FollowPartnerResponse
import com.echoed.chamber.services.partner.PartnerClientCredentials
import com.echoed.chamber.services.echoeduser.PartnerFollower
import com.echoed.chamber.services.partner.RequestPartnerFollowersResponse


@Controller
@RequestMapping(Array("/api/partner"))
class PartnerUserController extends EchoedController {


    private val failAsZero = failAsValue(classOf[NFE])(0)
    private def parse(number: String) =  failAsZero { Integer.parseInt(number) }

    @RequestMapping(value = Array("{partnerId}", "{partnerId}/stories"), method=Array(RequestMethod.GET))
    @ResponseBody
    def partnerFeed(
                       @PathVariable(value = "partnerId") partnerId: String,
                       @RequestParam(value = "page", required = false) page: String,
                       @RequestParam(value = "origin", required = false, defaultValue = "echoed") origin: String) = {

        val result = new DeferredResult[Feed[PartnerContext]](null, ErrorResult.timeout)

        log.debug("Requesting for Partner Content for Partner {}", partnerId )

        mp(RequestPartnerContent(new PartnerClientCredentials(partnerId), parse(page), origin, "Story")).onSuccess {
            case RequestPartnerContentResponse(_, Right(partnerFeed)) => result.setResult(partnerFeed)
        }

        result
    }

    @RequestMapping(value = Array("{partnerId}/photos"), method=Array(RequestMethod.GET))
    @ResponseBody
    def partnerFeedPhotos(
                             @PathVariable(value = "partnerId") partnerId: String,
                             @RequestParam(value = "page", required = false) page: String,
                             @RequestParam(value = "origin", required = false, defaultValue = "echoed") origin: String) = {

        val result = new DeferredResult[Feed[PartnerContext]](null, ErrorResult.timeout)

        log.debug("Requesting for Partner Content for Partner {}", partnerId )

        mp(RequestPartnerContent(new PartnerClientCredentials(partnerId), parse(page), origin, "Photo")).onSuccess {
            case RequestPartnerContentResponse(_, Right(partnerFeed)) => result.setResult(partnerFeed)
        }
        result
    }

    @RequestMapping(value = Array("{partnerId}/followers"), method = Array(RequestMethod.GET))
    @ResponseBody
    def partnerFollowers( @PathVariable(value = "partnerId") partnerId: String) = {

        val result = new DeferredResult[Feed[PartnerContext]](null, ErrorResult.timeout)

        mp(RequestPartnerFollowers(new PartnerClientCredentials(partnerId))).onSuccess {
            case RequestPartnerFollowersResponse(_, Right(partnerFeed)) => result.setResult(partnerFeed)
        }

        result
    }

    @RequestMapping(value = Array("/{partnerId}/followers"), method = Array(RequestMethod.PUT))
    @ResponseBody
    def followPartner(
        @PathVariable(value = "partnerId") partnerId: String,
        eucc: EchoedUserClientCredentials) = {

        val result = new DeferredResult[List[PartnerFollower]](null, ErrorResult.timeout)

        mp(FollowPartner(eucc, partnerId)).onSuccess {
            case FollowPartnerResponse(_, Right(fp)) => result.setResult(fp)
        }
        result
    }

    @RequestMapping(value = Array("/{partnerId}/followers"), method = Array(RequestMethod.DELETE))
    @ResponseBody
    def unFollowPartner(
        @PathVariable(value = "partnerId") partnerId: String,
        eucc: EchoedUserClientCredentials) = {

        val result = new DeferredResult[List[PartnerFollower]](null, ErrorResult.timeout)

        mp(UnFollowPartner(eucc, partnerId)).onSuccess {
            case UnFollowPartnerResponse(_, Right(fp)) => result.setResult(fp)
        }
        result

    }



}