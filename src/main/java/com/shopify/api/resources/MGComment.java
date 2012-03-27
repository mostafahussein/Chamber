/**
 * DO NOT MODIFY THIS CODE
 *
 * Place all of your changes in Comment.java
 *
 * It has been machine generated from fixtures and your changes will be
 * lost if anything new needs to be added to the API.
 **/
// Last Generated: 2011-09-26T15:53:49-04:00
package com.shopify.api.resources;

import java.util.List;
import org.codehaus.jackson.annotate.JsonProperty;

/**
 * This code has been machine generated by processing the single entry
 * fixtures found from the Shopify API Documentation
 */

public class MGComment extends ShopifyResource {

	@JsonProperty("article_id")
	public int getArticleId() {
		Integer value = (Integer)getAttribute("article_id");
		return value != null ? value : 0;
	}
	@JsonProperty("article_id")
	public void setArticleId(int _article_id) {
		setAttribute("article_id", _article_id);
	}

	@JsonProperty("author")
	public String getAuthor() {
		return (String)getAttribute("author");
	}
	@JsonProperty("author")
	public void setAuthor(String _author) {
		setAttribute("author", _author);
	}

	@JsonProperty("blog_id")
	public int getBlogId() {
		Integer value = (Integer)getAttribute("blog_id");
		return value != null ? value : 0;
	}
	@JsonProperty("blog_id")
	public void setBlogId(int _blog_id) {
		setAttribute("blog_id", _blog_id);
	}

	@JsonProperty("body")
	public String getBody() {
		return (String)getAttribute("body");
	}
	@JsonProperty("body")
	public void setBody(String _body) {
		setAttribute("body", _body);
	}

	@JsonProperty("body_html")
	public String getBodyHtml() {
		return (String)getAttribute("body_html");
	}
	@JsonProperty("body_html")
	public void setBodyHtml(String _body_html) {
		setAttribute("body_html", _body_html);
	}

	@JsonProperty("email")
	public String getEmail() {
		return (String)getAttribute("email");
	}
	@JsonProperty("email")
	public void setEmail(String _email) {
		setAttribute("email", _email);
	}

	@JsonProperty("ip")
	public String getIp() {
		return (String)getAttribute("ip");
	}
	@JsonProperty("ip")
	public void setIp(String _ip) {
		setAttribute("ip", _ip);
	}

	@JsonProperty("published_at")
	public String getPublishedAt() {
		return (String)getAttribute("published_at");
	}
	@JsonProperty("published_at")
	public void setPublishedAt(String _published_at) {
		setAttribute("published_at", _published_at);
	}

	@JsonProperty("status")
	public String getStatus() {
		return (String)getAttribute("status");
	}
	@JsonProperty("status")
	public void setStatus(String _status) {
		setAttribute("status", _status);
	}

	@JsonProperty("user_agent")
	public String getUserAgent() {
		return (String)getAttribute("user_agent");
	}
	@JsonProperty("user_agent")
	public void setUserAgent(String _user_agent) {
		setAttribute("user_agent", _user_agent);
	}

}
