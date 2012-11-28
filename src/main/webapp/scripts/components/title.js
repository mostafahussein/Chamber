define(
    [
        'jquery',
        'backbone',
        'underscore',
        'components/utils',
        'text!templates/title.html',
        'text!templates/title/topic.html'
    ],
    function($, Backbone, _, utils, templateTitle, templateTopic){
        return Backbone.View.extend({
            initialize: function(options){
                _.bindAll(this);
                this.element = $(options.el);
                this.properties = options.properties;
                this.EvAg = options.EvAg;
                this.EvAg.bind('title/update', this.update);
                this.render(options);
            },
            events: {
            },
            loadTopics: function(){
                var self = this;
                utils.AjaxFactory({
                    url: this.properties.urls.api + "/api/topics/" + this.topicEndPoint,
                    dataType: 'json',
                    success: function(response){
                        var ul = $('<ul></ul>');
                        $.each(response, function(index, topic){
                            var template = _.template(templateTopic, topic);
                            $('<li></li>').html(template).appendTo(ul);
                        });
                        self.titleBody.append($('<div id="title-body-title">Suggested Topics</div>'));
                        ul.appendTo(self.titleBody);
                    }
                })();
            },
            render: function(options){
                this.element.html(_.template(templateTitle));
                this.titleText = $('#title-text');
                this.titleBody = $('#title-body');
                this.element.show();
            },
            update: function(options){
                this.titleText.text(options.title);
                this.titleBody.empty();
                if(options.imageUrl) {
                    this.element.css('background-image', 'url("' + options.imageUrl + '")');
                } else {
                    this.element.css('background-image','');
                }
                switch(options.type){
                    case "partner":
                        this.topicEndPoint = "partner/" + options.partnerId;
                        this.loadTopics();
                        this.titleBody.show();
                        break;
                    case "community":
                        this.topicEndPoint = "community/" + options.communityId;
                        this.loadTopics();
                        this.titleBody.show();
                        break;
                    case "echoed":
                        this.topicEndPoint = "";
                        this.loadTopics();
                        this.titleBody.show();
                        break;
                    default:
                        this.titleBody.hide();
                        break;
                }
            }
        });
    }
)
