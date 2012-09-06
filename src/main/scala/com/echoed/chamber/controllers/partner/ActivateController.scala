package com.echoed.chamber.controllers.partner

import org.springframework.stereotype.Controller
import javax.servlet.http.{HttpServletRequest, HttpServletResponse}
import javax.validation.Valid
import org.springframework.validation.BindingResult
import org.springframework.web.bind.annotation.{PathVariable, RequestMapping, RequestMethod}
import com.echoed.chamber.services.partneruser._
import org.springframework.web.servlet.ModelAndView
import org.springframework.web.context.request.async.DeferredResult
import com.echoed.chamber.controllers.{FormController, EchoedController, Errors}
import org.springframework.beans.factory.annotation.Autowired
import com.echoed.chamber.controllers.interceptors.Secure
import com.echoed.chamber.domain.InvalidPassword



@Controller
@Secure
class ActivateController extends EchoedController with FormController {

    @Autowired var formValidator: ActivateFormValidator = _

    @RequestMapping(value = Array("/partner/activate/{code}"), method = Array(RequestMethod.GET))
    def activateGet(
            @PathVariable("code") code: String,
            request: HttpServletRequest,
            response: HttpServletResponse) = {
        val result = new DeferredResult(new ModelAndView(v.activateView))

        log.debug("Starting activation with code {}", code)

        mp(LoginWithCode(code)).onSuccess {
            case LoginWithCodeResponse(_, Right(partnerUser)) =>
                cookieManager.addPartnerUserCookie(response, partnerUser, request)
                val modelAndView = new ModelAndView(v.activateView)
                modelAndView.addObject("partnerUser", partnerUser)
                modelAndView.addObject("activateForm", new ActivateForm(partnerUser.id))
                result.set(modelAndView)
                log.debug("Showing activation form for partner user {}: {}", partnerUser.id, partnerUser.name)
        }

        result
    }

    @RequestMapping(value = Array("/partner/activate"), method = Array(RequestMethod.POST))
    def activatePost(
            @Valid activateForm: ActivateForm,
            bindingResult: BindingResult,
            pucc: PartnerUserClientCredentials) = {

        val errorModelAndView = new ModelAndView(v.activateView) with Errors

        if (!bindingResult.hasErrors) {
            formValidator.validate(activateForm, bindingResult)
        }

        if (bindingResult.hasErrors) {
            errorModelAndView
        } else {
            val result = new DeferredResult()

            log.debug("Activating partner user {}", activateForm.partnerUserId)

            mp(ActivatePartnerUser(pucc, activateForm.password)).onComplete(_.fold(
                e => {
                    errorModelAndView.addError(e)
                    result.set(errorModelAndView)
                },
                _ match {
                    case ActivatePartnerUserResponse(_, Left(e: InvalidPassword)) =>
                        bindingResult.rejectValue("password", e.code.get, e.message)
                        result.set(errorModelAndView)
                    case ActivatePartnerUserResponse(_, Right(partnerUser)) =>
                        val modelAndView = new ModelAndView(v.postActivateView)
                        modelAndView.addObject("partnerUser", partnerUser)
                        result.set(modelAndView)
                        log.debug("Activated partner user {}: {}", partnerUser.id, partnerUser.name)
                    case ActivatePartnerUserResponse(_, Left(e)) =>
                        errorModelAndView.addError(e)
                        result.set(errorModelAndView)
            }))

            result
        }
    }

}
