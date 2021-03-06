define(
    [
        'jquery',
        'backbone',
        'underscore'
    ],
    function($, Backbone, _){
        return Backbone.Router.extend({
            initialize: function(options){
                _.bindAll(this);
                this.EvAg = options.EvAg;
                this.properties = options.properties;
                this.exhibitLoaded = false;
            },
            routes: {
                "": "home",
                "story/:id" : "story",
                "!story/:id" : "story",
                "login": "login",
                "user/:id": "user",
                "partner/:id": "partner"
            },
            partner: function(id){
                this.loadPage('partner', { endPoint: "/partner/" + id });
            },
            user: function(id){
                this.loadPage('user', { endPoint: "/user/" + id });
            },
            login: function(){
                this.EvAg.trigger('page/show', "login");
                this.lastPage = "login"
            },
            loadPage: function(page, options){
                this.EvAg.trigger('exhibit/init', options);
                this.EvAg.trigger('page/show', 'exhibit');
                this.exhibitLoaded = true;
            },
            home: function(){
                this.loadPage("explore", { endPoint: "/me/feed", title: "" });
                this.lastPage = ""
            },
            story: function(id){
                if(this.exhibitLoaded === false) this.EvAg.trigger('exhibit/init', { endPoint: "/me/feed", title: "" });
                if(this.lastPage ===  'story') {
                    this.EvAg.trigger('story/change', id);
                } else {
                    this.EvAg.trigger('story/show', id);
                    this.EvAg.trigger('page/show', 'story');
                }
                this.lastPage = 'story';
            }
        })
    }
)