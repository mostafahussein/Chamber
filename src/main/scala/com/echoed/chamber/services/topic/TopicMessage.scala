package com.echoed.chamber.services.topic
import com.echoed.chamber.services.{MessageResponse => MR, Event, EchoedException, Message}
import com.echoed.chamber.domain.Topic
import com.echoed.chamber.domain.views.Feed
import com.echoed.chamber.domain.views.context.TopicContext

sealed trait TopicMessage extends Message
sealed case class TopicException(
    message: String = "",
    cause: Throwable = null,
    code: Option[String] = None,
    arguments: Option[Array[AnyRef]] = None) extends EchoedException(message, cause, code, arguments)

import com.echoed.chamber.services.topic.{TopicMessage => TM}
import com.echoed.chamber.services.topic.{TopicException => TE}


case class ReadTopics() extends TM
case class ReadTopicsResponse(
            message: ReadTopics,
            value: Either[TE, List[Topic]]) extends TM with MR [List[Topic], ReadTopics, TE]

case class ReadTopicFeed(topicId: String, page: Int) extends TM
case class ReadTopicFeedResponse(
            message: ReadTopicFeed,
            value: Either[TE, Feed[TopicContext]]) extends TM with MR [Feed[TopicContext], ReadTopicFeed, TE]

case class ReadCommunityTopics(communityId: String) extends TM
case class ReadCommunityTopicsResponse(
            message: ReadCommunityTopics,
            value: Either[TE, List[Topic]]) extends TM with MR [List[Topic], ReadCommunityTopics, TE]

private[services] case class RequestTopic(topicId: String) extends TM
private[services] case class RequestTopicResponse(
            message: RequestTopic,
            value: Either[TE, Topic]) extends TM with MR[Topic, RequestTopic, TE]


