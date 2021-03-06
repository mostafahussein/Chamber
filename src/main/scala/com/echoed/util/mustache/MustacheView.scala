package com.echoed.util.mustache

import org.springframework.web.servlet.view.AbstractTemplateView
import scala.reflect.BeanProperty
import com.github.mustachejava.Mustache
import java.util.{Map => JMap}
import javax.servlet.http.{HttpServletResponse, HttpServletRequest}

class MustacheView extends AbstractTemplateView {

    @BeanProperty var template: Mustache = null


    protected override def renderMergedTemplateModel(
            model: JMap[String, Object],
            request: HttpServletRequest,
            response: HttpServletResponse) {

        response.setContentType(getContentType())
        val writer = response.getWriter()
        try {
            template.execute(writer, model)
        } finally {
            writer.flush()
        }
    }

    override def getContentType() = {
        if (getBeanName.endsWith(".js")) "application/x-javascript; charset=UTF-8"
        else if (getBeanName.endsWith(".json")) "application/json; charset=UTF-8"
        else if (getBeanName.endsWith(".txt")) "text/plain; charset=UTF-8"
        else if (getBeanName.endsWith(".xml")) "application/xml; charset=UTF-8"
        else "text/html; charset=UTF-8"
    }

}
