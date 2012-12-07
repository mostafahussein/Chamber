define(
    [
        'jquery',
        'backbone',
        'underscore',
        'easyXDM'
    ],
    function($, Backbone, _, easyXDM){
        return Backbone.View.extend({
            initialize: function(options){
                _.bindAll(this);
                var self = this;
                this.EvAg = options.EvAg;
                this.properties = options.properties;
                this.htmlEl = $('html');
                this.socket = new easyXDM.Socket({
                    remote: this.properties.urls.api + "/widget/iframe/?pid=" + this.properties.partnerId,
                    props: {
                        id: "echoed-overlay"
                    },
                    onReady: function(){
                        self.element = $('#echoed-overlay');
                        self.element.removeAttr('style');
                        self.parseHash();
                        window.onhashchange = self.parseHash;
                        if(window.addEventListener) window.addEventListener('message', self.handleMessage, false);
                        else window.attachEvent('onmessage', self.handleMessage);
                    },
                    onMessage: function(message, origin){
                        self.handleMessage(message, origin);
                    }
                });
                this.EvAg.bind('overlay/show', this.showOverlay);
            },
            handleMessage: function(message, origin){
                if(message.data === "echoed-close"){
                    var hash = window.location.hash;
                    var index = hash.indexOf('echoed');
                    if(index > 0) window.location.hash = hash.substr(0, index);
                    this.hideOverlay();
                }
            },
            hideOverlay: function(){
                var self = this;
                this.element.fadeOut(function(){
                    self.htmlEl.css({"overflow": "auto" });
                });
            },
            showOverlay: function(type, data){
                type = typeof type == 'undefined' ? "hash" : type;
                data = typeof data == 'undefined' ? "#home" : data;
                this.htmlEl.css({ "overflow" : "hidden" });
                this.element.fadeIn();
                this.socket.postMessage(JSON.stringify({ type:  type, data: data}));
            },
            parseHash: function(){
                var hash = window.location.hash;
                var index = hash.indexOf('echoed');
                if(index > 0){
                    var iFrameHash = '#home';
                    var endPoint = hash.substr(index).split('_')[1];
                    if(endPoint) iFrameHash = '#' + endPoint;
                    this.showOverlay('hash', iFrameHash);
                }
            }
        });
    }
);