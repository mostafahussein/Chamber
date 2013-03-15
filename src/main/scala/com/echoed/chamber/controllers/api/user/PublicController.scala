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
import com.echoed.chamber.domain.views.content.{Content, PhotoContent, ContentDescription}
import views.Feed
import com.echoed.chamber.domain.views.context._
import com.echoed.chamber.services.feed._


@Controller
@RequestMapping(Array("/api"))
class PublicController extends EchoedController {


    private val failAsZero = failAsValue(classOf[NFE])(0)
    private def parse(number: String) =  failAsZero { Integer.parseInt(number) }

    @RequestMapping(value = Array("/public/feed"), method = Array(RequestMethod.GET))
    @ResponseBody
    def getPublicStories(@RequestParam(value = "page", required = false) page: String) =
            getPublicContent(Content.defaultContentDescription, page)

    @RequestMapping(value = Array("/public/feed/{contentType}"), method = Array(RequestMethod.GET))
    @ResponseBody
    def getPublicFeed(
            @PathVariable(value = "contentType") contentType: String,
            @RequestParam(value = "page", required = false) page: String) =
        getPublicContent(Content.getContentDescription(contentType), page)


    def getPublicContent(contentType: ContentDescription, page: String) = {
        val result = new DeferredResult[Feed[PublicContext]](null, ErrorResult.timeout)

        mp(RequestPublicContent(contentType, parse(page))).onSuccess {
            case RequestPublicContentResponse(_, Right(feed)) => result.setResult(feed)
        }
        result
    }

}