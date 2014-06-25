app = require('akylas.commonjs').createApp(this, { // not using var seems very important, cant really see why!
    modules: {
        slidemenu: require('akylas.slidemenu'),
        location: require('akylas.location'),
        iconicfont: require('lib/IconicFont')
    },
    iconicfonts: {
        webhostinghub: '/fonts/font_webhostinghub',
        // elusive: '/fonts/font_elusive'
    },
    commonjsOptions: {
        underscore: 'lodash',
        modules: ['ti', 'moment', 'lang'],
        additions: ['string']
    },
    // templatesPreRjss: ['text'],
    // templates: ['row', 'view'],
    // utilities: true,
    mappings: [
        ['slidemenu', 'SlideMenu', 'SlideMenu']
    ],
    // ifApple: function(app) {
    //     if (app.info.deployType !== 'production') {
    //         app.modules.testflight = require('ti.testflight');
    //     }
    // },
    windowManager: true
});

app.main();

var isiOS7 = app.deviceinfo.isIOS7;
if (__APPLE__) {
    isiOS7 = parseInt(Titanium.Platform.version.split(".")[0]) >= 7;
}
var backColor = 'white';
var textColor = 'black';
var navGroup;
var openWinArgs;
var html =
    '  SUCCESS     <font color="red">musique</font> électronique <b><span style="background-color:green;border-color:black;border-radius:20px;border-width:1px">est un type de </span><big><big>musique</big></big> qui a <font color="green">été conçu à</font></b> partir des années<br> 1950 avec des générateurs de signaux<br> et de sons synthétiques. Avant de pouvoir être utilisée en temps réel, elle a été primitivement enregistrée sur bande magnétique, ce qui permettait aux compositeurs de manier aisément les sons, par exemple dans l\'utilisation de boucles répétitives superposées. Ses précurseurs ont pu bénéficier de studios spécialement équipés ou faisaient partie d\'institutions musicales pré-existantes. La musique pour bande de Pierre Schaeffer, également appelée musique concrète, se distingue de ce type de musique dans la mesure où son matériau primitif était constitué des sons de la vie courante. La particularité de la musique électronique de l\'époque est de n\'utiliser que des sons générés par des appareils électroniques.';
// html = '<span style="border-style:solid;background-color:green;border-color:red;border-radius:20px;border-width:3px;padding-top:3px;padding-bottom:3px;line-height:2em;"> SUCCESS </span><br><span style="border-style:solid;background-color:green;border-color:red;border-radius:20px;border-width:3px;padding-top:0px;padding-bottom:0px;line-height:1em;"> SUCCESS </span>'
if (__ANDROID__) {
    backColor = 'black';
    textColor = 'gray';
}

function merge_options(obj1, obj2, _new) {
    _new = _new === true;
    var newObject = obj1;
    if (_new === true) {
        newObject = JSON.parse(JSON.stringify(obj1));
    }
    for (var attrname in obj2) {
        newObject[attrname] = obj2[attrname];
    }
    return newObject;
}
var initWindowArgs = {
    backgroundColor: backColor,
    orientationModes: [Ti.UI.UPSIDE_PORTRAIT,
        Ti.UI.PORTRAIT,
        Ti.UI.LANDSCAPE_RIGHT,
        Ti.UI.LANDSCAPE_LEFT
    ]
};
if (isiOS7) {
    initWindowArgs = merge_options(initWindowArgs, {
        autoAdjustScrollViewInsets: true,
        extendEdges: [Ti.UI.EXTEND_EDGE_ALL],
        translucent: true
    });
}

function createWin(_args) {
    return new Window(merge_options(initWindowArgs, _args, true));
}

function createListView(_args, _addEvents) {
    var realArgs = merge_options({
        allowsSelection: false,
        unHighlightOnSelect: false,
        rowHeight: 50,
        selectedBackgroundGradient: {
            type: 'linear',
            colors: [{
                color: '#1E232C',
                offset: 0.0
            }, {
                color: '#3F4A58',
                offset: 0.2
            }, {
                color: '#3F4A58',
                offset: 0.8
            }, {
                color: '#1E232C',
                offset: 1
            }],
            startPoint: {
                x: 0,
                y: 0
            },
            endPoint: {
                x: 0,
                y: "100%"
            }
        }
    }, _args);
    var listview = Ti.UI.createListView(realArgs);
    if (_addEvents !== false) {
        listview.addEventListener('itemclick', function(_event) {
            info('itemclick ' + _event.itemIndex);
            if (_event.hasOwnProperty('section') && _event.hasOwnProperty('itemIndex')) {
                var item = _event.section.getItemAt(_event.itemIndex);
                if (item.callback) {
                    item.callback(item.properties);
                }
            }
        });
    }

    return listview;
}

function varSwitch(_var, _val1, _val2) {
    return (_var === _val1) ? _val2 : _val1;
}
var androidActivitysSettings = {
    actionBar: {
        displayHomeAsUp: true,
        onHomeIconItemSelected: function(e) {
            e.window.close();
        }
    }
};

function openWin(_win, _withoutActionBar) {
    if (__ANDROID__) {
        if (_withoutActionBar !== true) _win.activity = androidActivitysSettings;
    }
    mainWin.openWindow(_win);
}

function transformExs() {
    var win = createWin({
        swipeToClose: true,

        title: "transform"
    });
    var listview = createListView();
    listview.sections = [{
        items: [{
            properties: {
                title: 'Transform',
                backgroundColor: cellColor(1)
            },
            callback: transform1Ex
        }, {
            properties: {
                title: 'TransformAnimated'
            },
            callback: transform2Ex
        }, {
            properties: {
                title: 'PopIn'
            },
            callback: transform3Ex
        }, {
            properties: {
                title: 'SlideIn'
            },
            callback: transform4Ex
        }, {
            properties: {
                title: 'ListView'
            },
            callback: transform5Ex
        }, {
            properties: {
                title: 'AnchorPoint'
            },
            callback: transform6Ex
        }]
    }];
    win.add(listview);
    openWin(win);
}

function transform1Ex() {
    var win = createWin();
    var button = Ti.UI.createButton({
        top: 50,
        bubbleParent: false,
        title: 'test buutton'
    });
    var t1 = '';
    info(t1.toString());
    var t2 = 'os2t40,100%r90';
    info(t2.toString());
    button.addEventListener('longpress', function(e) {
        button.animate({
            duration: 500,
            transform: varSwitch(button.transform, t2, t1),
        });
    });
    win.add(button);
    var label = Ti.UI.createLabel({
        bottom: 20,
        backgroundColor: 'gray',
        backgroundSelectedColor: '#ddd',
        bubbleParent: false,
        text: 'This is a sample\n text for a label'
    });
    var t3 = 's2t0,-40r90';
    label.addEventListener('longpress', function(e) {
        label.animate({
            duration: 500,
            transform: varSwitch(label.transform, t3, t1),
        });
    });
    win.add(label);
    openWin(win);
}

function transform2Ex() {
    var gone = false;
    var win = createWin();
    var t0 = Ti.UI.create2DMatrix({
        anchorPoint: {
            x: 0,
            y: "100%"
        }
    });
    var t1 = t0.rotate(30);
    var t2 = t0.rotate(145);
    var t3 = t0.rotate(135);
    var t4 = t0.translate(0, "100%").rotate(125);
    var t5 = Ti.UI.create2DMatrix().translate(0, ((Math.sqrt(2)) * 100)).rotate(180);
    var view = Ti.UI.createView({
        transform: t0,
        borderRadius: 20,
        borderColor: 'orange',
        borderWidth: 2,
        // backgroundPadding: {
        //	left: 10,
        //	right: 10,
        //	bottom: -5
        // },
        clipChildren: false,
        backgroundColor: 'yellow',
        backgroundGradient: {
            type: 'radial',
            colors: ['orange', 'yellow']
        },
        top: 30,
        width: 200,
        height: 100
    });
    var anim1 = Ti.UI.createAnimation({
        id: 1,
        cancelRunningAnimations: true,
        duration: 800,
        transform: t1
    });
    var animToRun = anim1;
    anim1.addEventListener('complete', function() {
        animToRun = anim2;
        view.animate(anim2);
    });
    var anim2 = Ti.UI.createAnimation({
        id: 2,
        cancelRunningAnimations: true,
        duration: 800,
        transform: t2
    });
    anim2.addEventListener('complete', function() {
        animToRun = anim3;
        view.animate(anim3);
    });
    var anim3 = Ti.UI.createAnimation({
        id: 3,
        cancelRunningAnimations: true,
        duration: 500,
        transform: t3
    });
    anim3.addEventListener('complete', function() {
        animToRun = anim5;
        view.animate(anim5);
    });
    var anim4 = Ti.UI.createAnimation({
        id: 4,
        cancelRunningAnimations: true,
        duration: 500,
        transform: t4
    });
    anim4.addEventListener('complete', function() {
        gone = true;
    });
    var anim5 = Ti.UI.createAnimation({
        id: 5,
        cancelRunningAnimations: true,
        duration: 4000,
        bottom: 145,
        width: 200,
        top: null
    });
    anim5.addEventListener('complete', function() {
        animToRun = anim6;
        view.animate(anim6);
    });
    var anim6 = Ti.UI.createAnimation({
        id: 6,
        cancelRunningAnimations: true,
        duration: 400,
        transform: t5
    });
    anim6.addEventListener('complete', function() {
        animToRun = anim1;
        gone = true;
    });

    function onclick() {
        if (gone === true) {
            view.animate({
                duration: 6000,
                transform: t0,
                top: 30,
                width: 100,
                bottom: null
            }, function() {
                gone = false;
            });
        } else view.animate(animToRun);
    }
    win.addEventListener('click', onclick);
    win.add(view);
    openWin(win);
}

function transform3Ex() {
    var win = createWin();
    var t = 's0.3';
    var view = Ti.UI.createView({
        backgroundColor: 'red',
        borderRadius: [12, 4, 0, 40],
        disableHW: true,
        borderColor: 'green',
        borderPadding: {
            left: 10,
            right: 10,
            bottom: -5
        },
        borderWidth: 2,
        opacity: 0,
        width: 200,
        height: 200
    });
    view.add(Ti.UI.createView({
        borderColor: 'yellow',
        borderWidth: 2,
        backgroundColor: 'blue',
        bottom: 0,
        width: Ti.UI.FILL,
        height: 50
    }));
    var showMe = function() {
        view.opacity = 0;
        view.transform = t;
        win.add(view);
        view.animate({
            duration:200,
            transform: '',
            // autoreverse: true,
            opacity: 1,
            curve: [0.17,0.67,0.86,1.57]
        });
    };
    var hideMe = function(_callback) {
        view.animate({
            duration:200,
            opacity: 0
        }, function() {
            win.remove(view);
        });
    };
    var button = Ti.UI.createButton({
        bottom: 10,
        width: 100,
        bubbleParent: false,
        title: 'test buutton'
    });
    button.addEventListener('click', function(e) {
        if (view.opacity === 0) showMe();
        else hideMe();
    });
    win.add(button);
    openWin(win);
}

function transform4Ex() {
    var win = createWin();
    var t0 = Ti.UI.create2DMatrix();
    var t1 = t0.translate("-100%", 0);
    var t2 = t0.translate("100%", 0);
    var view = Ti.UI.createView({
        backgroundColor: 'red',
        opacity: 0,
        transform: t1,
        width: 200,
        height: 200
    });
    view.add(Ti.UI.createView({
        backgroundColor: 'blue',
        bottom: 10,
        width: 50,
        height: 50
    }));
    var showMe = function() {
        view.transform = t1;
        win.add(view);
        view.animate({
            duration: 3000,
            transform: t0,
            opacity: 1
        });
    };
    var hideMe = function(_callback) {
        view.animate({
            duration: 3000,
            transform: t2,
            opacity: 0
        }, function() {
            win.remove(view);
        });
    };
    var button = Ti.UI.createButton({
        bottom: 10,
        width: 100,
        bubbleParent: false,
        title: 'test buutton'
    });
    button.addEventListener('click', function(e) {
        if (view.opacity === 1) hideMe();
        else showMe();
    });
    win.add(button);
    openWin(win);
}

function transform5Ex() {
    var showItemIndex = -1;
    var showItemSection = null;

    function hideMenu() {
        if (showItemIndex != -1 && showItemSection !== null) {
            var hideItem = showItemSection.getItemAt(showItemIndex);
            hideItem.menu.transform = t1;
            hideItem.menu.opacity = 0;
            showItemSection.updateItemAt(showItemIndex, hideItem);
            showItemIndex = -1;
            showItemSection = null;
        }
    }
    var win = createWin();
    var t0 = Ti.UI.create2DMatrix();
    var t1 = t0.translate(0, "100%");
    var myTemplate = {
        childTemplates: [{
            type: 'Ti.UI.View',
            bindId: 'holder',
            properties: {
                width: Ti.UI.FILL,
                height: Ti.UI.FILL,
                touchEnabled: false,
                layout: 'horizontal',
                horizontalWrap: false
            },
            childTemplates: [{
                type: 'Ti.UI.ImageView',
                bindId: 'pic',
                properties: {
                    touchEnabled: false,
                    width: 50,
                    height: 50
                }
            }, {
                type: 'Ti.UI.Label',
                bindId: 'info',
                properties: {
                    color: textColor,
                    touchEnabled: false,
                    font: {
                        size: 20,
                        weight: 'bold'
                    },
                    width: Ti.UI.FILL,
                    left: 10
                }
            }, {
                type: 'Ti.UI.Button',
                bindId: 'button',
                properties: {
                    title: 'menu',
                    left: 10
                },
                events: {
                    'click': function(_event) {
                        if (_event.hasOwnProperty('section') && _event.hasOwnProperty('itemIndex')) {
                            hideMenu();
                            var item = _event.section.getItemAt(_event.itemIndex);
                            item.menu = {
                                transform: t0,
                                opacity: 1
                            };
                            showItemIndex = _event.itemIndex;
                            showItemSection = _event.section;
                            _event.section.updateItemAt(_event.itemIndex, item);
                        }
                    }
                }
            }]
        }, {
            type: 'Ti.UI.Label',
            bindId: 'menu',
            properties: {
                color: 'white',
                text: 'I am the menu',
                backgroundColor: '#444',
                width: Ti.UI.FILL,
                height: Ti.UI.FILL,
                opacity: 0,
                transform: t1
            },
            events: {
                'click': hideMenu
            }
        }]
    };
    var listView = createListView({
        templates: {
            'template': myTemplate
        },
        defaultItemTemplate: 'template'
    });
    var sections = [{
        headerTitle: 'Fruits / Frutas',
        items: [{
            info: {
                text: 'Apple'
            }
        }, {
            properties: {
                backgroundColor: 'red'
            },
            info: {
                text: 'Banana'
            },
            pic: {
                image: 'banana.png'
            }
        }]
    }, {
        headerTitle: 'Vegetables / Verduras',
        items: [{
            info: {
                text: 'Carrot'
            }
        }, {
            info: {
                text: 'Potato'
            }
        }, {
            info: {
                text: 'Potato'
            }
        }, {
            info: {
                text: 'Potato'
            }
        }, {
            info: {
                text: 'Potato'
            }
        }, {
            info: {
                text: 'Potato'
            }
        }, {
            info: {
                text: 'Potato'
            }
        }, {
            info: {
                text: 'Potato'
            }
        }, {
            info: {
                text: 'Potato'
            }
        }, {
            info: {
                text: 'Potato'
            }
        }, {
            info: {
                text: 'Potato'
            }
        }, {
            info: {
                text: 'Potato'
            }
        }, {
            info: {
                text: 'Potato'
            }
        }, {
            info: {
                text: 'Potato'
            }
        }, {
            info: {
                text: 'Potato'
            }
        }, {
            info: {
                text: 'Potato'
            }
        }, {
            info: {
                text: 'Potato'
            }
        }, {
            info: {
                text: 'Potato'
            }
        }, {
            info: {
                text: 'Potato'
            }
        }, {
            info: {
                text: 'Potato'
            }
        }, {
            info: {
                text: 'Potato'
            }
        }, {
            info: {
                text: 'Potato'
            }
        }]
    }, {
        headerTitle: 'Grains / Granos',
        items: [{
            info: {
                text: 'Corn'
            }
        }, {
            info: {
                text: 'Rice'
            }
        }]
    }];
    listView.setSections(sections);
    win.add(listView);
    openWin(win);
}

function transform6Ex() {
    var win = createWin();
    var t = Ti.UI.create2DMatrix().rotate(90);
    var view = Ti.UI.createView({
        transform: null,
        backgroundColor: 'red',
        borderRadius: 12,
        borderColor: 'green',
        borderWidth: 2,
        width: 200,
        height: 200
    });
    win.add(view);
    var bid = -1;

    function createBtn(_title) {
        bid++;
        return {
            type: 'Ti.UI.Button',
            left: 0,
            bid: bid,
            title: _title
        }
    }

    win.add({
        properties: {
            width: 'SIZE',
            height: 'SIZE',
            left: 0,
            layout: 'vertical'
        },
        childTemplates: [
            createBtn('topright'),
            createBtn('bottomright'),
            createBtn('bottomleft'),
            createBtn('topleft'),
            createBtn('center'),
        ]
    });
    win.addEventListener('click', function(e) {
        if (e.source.bid !== undefined) {
            info(e.source.bid);
            var anchorPoint = {
                x: 0,
                y: 0
            };
            switch (e.source.bid) {
                case 0:
                    anchorPoint.x = 1;
                    break;
                case 1:
                    anchorPoint.x = 1;
                    anchorPoint.y = 1;
                    break;
                case 2:
                    anchorPoint.y = 1;
                    break;
                case 3:
                    break;
                case 4:
                    anchorPoint.x = 0.5;
                    anchorPoint.y = 0.5;
                    break;
            }
            view.anchorPoint = anchorPoint;
        } else {
            // view.transform = (view.transform === null)?t:null;
            view.animate({
                transform: (view.transform === null) ? t : null,
                duration: 500
            });
        }
    });
    openWin(win);
}

function layoutExs() {
    var win = createWin();
    var listview = createListView();
    listview.sections = [{
        items: [{
            properties: {
                title: 'Animated Horizontal'
            },
            callback: layout1Ex
        }]
    }];
    win.add(listview);
    openWin(win);
}

function layout1Ex() {
    var win = createWin();
    var view = Ti.UI.createView({
        backgroundColor: 'green',
        width: 200,
        top: 0,
        height: 300,
        layout: 'horizontal'
    });
    var view1 = Ti.UI.createView({
        backgroundColor: 'red',
        width: 60,
        height: 80,
        left: 0
    });
    var view2 = Ti.UI.createView({
        backgroundColor: 'blue',
        width: 20,
        borderColor: 'red',
        borderWidth: 2,
        borderRadius: [2, 10, 0, 20],
        // top:10,
        height: 80,
        left: 10,
        right: 4
    });
    view2.add(Ti.UI.createView({
        backgroundColor: 'orange',
        width: 10,
        height: 20,
        top: 0
    }));
    var view3 = Ti.UI.createView({
        backgroundColor: 'orange',
        width: Ti.UI.FILL,
        height: Ti.UI.FILL,
        maxHeight: 100,
        bottom: 6,
        right: 4
    });
    view.add(view1);
    view.add(view2);
    view.add({
        type: 'Ti.UI.View',
        properties: {
            backgroundColor: 'purple',
            width: Ti.UI.FILL,
            height: Ti.UI.FILL,
            bottom: 4,
            right: 4
        },
        childTemplates: [{
            type: 'Ti.UI.View',
            properties: {
                backgroundColor: 'orange',
                width: 50,
                height: 20,
                bottom: 0
            }
        }]

    });
    view.add(view3);
    win.add(view);
    win.add({
        type: 'Ti.UI.View',
        properties: {
            backgroundColor: 'yellow',
            width: 200,
            bottom: 0,
            height: Ti.UI.SIZE,
            layout: 'horizontal',
            horizontalWrap: true
        },
        childTemplates: [{
            type: 'Ti.UI.View',
            properties: {
                backgroundColor: 'red',
                width: 60,
                height: 80,
                left: 0
            }
        }, {
            type: 'Ti.UI.View',
            properties: {
                backgroundColor: 'blue',
                width: 20,
                borderColor: 'red',
                borderWidth: 2,
                borderRadius: [2, 10, 0, 20],
                // top:10,
                height: 80,
                left: 10,
                right: 4
            }
        }, {
            type: 'Ti.UI.View',
            properties: {
                backgroundColor: 'purple',
                width: Ti.UI.FILL,
                height: 100,
                bottom: 4,
                right: 4
            }
        }, {
            type: 'Ti.UI.View',
            properties: {
                backgroundColor: 'orange',
                width: 10,
                height: 50,
                maxHeight: 100,
                bottom: 6,
                right: 4
            }
        }]

    });
    win.addEventListener('click', function(e) {
        view2.animate({
            cancelRunningAnimations: true,
            // restartFromBeginning:true,
            duration: 3000,
            autoreverse: true,
            fullscreen: !view2.fullscreen
            // repeat: 4,
            // width: Ti.UI.FILL,
            // height: 100,
            // top: null,
            // left: 0,
            // right: 30
        });
    });
    openWin(win);
}

function buttonAndLabelEx() {
    var win = createWin({
        dispatchPressed: true,
        backgroundSelectedColor: 'green'
    });
    var button = Ti.UI.createButton({
        top: 0,
        padding: {
            left: 30,
            top: 30,
            bottom: 30,
            right: 30
        },
        height: 50,
        disableHW: true,
        bubbleParent: false,
        borderRadius: 10,
        borderColor: 'red',
        backgroundColor: 'gray',
        touchEnabled: false,
        backgroundSelectedGradient: {
            type: 'linear',
            colors: ['#333', 'transparent'],
            startPoint: {
                x: 0,
                y: 0
            },
            endPoint: {
                x: 0,
                y: "100%"
            }
        },
        title: 'test buutton'
    });
    button.add(Ti.UI.createView({
        enabled: false,
        backgroundColor: 'purple',
        backgroundSelectedColor: 'white',
        left: 10,
        width: 15,
        height: 15
    }));
    button.add(Ti.UI.createView({
        backgroundColor: 'green',
        bottom: 10,
        width: 15,
        height: 15
    }));
    button.add(Ti.UI.createView({
        backgroundColor: 'yellow',
        top: 10,
        width: 15,
        height: 15
    }));
    button.add(Ti.UI.createView({
        touchPassThrough: true,
        backgroundColor: 'orange',
        borderRadius: 1,
        right: 0,
        width: 35,
        height: Ti.UI.FILL
    }));
    var t1 = Ti.UI.create2DMatrix();
    var t2 = Ti.UI.create2DMatrix().scale(2.0, 2.0).translate(0, 40).rotate(90);
    button.addEventListener('longpress', function(e) {
        button.animate({
            duration: 500,
            transform: varSwitch(button.transform, t2, t1),
        });
    });

    win.add(button);
    var label = Ti.UI.createLabel({
        textAlign: 'center',
        width: 'FILL',
        height: 'FILL',
        verticalAlign: 'bottom',
        font: {
            weight: 'bold',
            size: 16
        },
        bottom: 20,
        height: 34,
        width: 140,
        // borderRadius:2,
        bubbleParent: false,
        // selectedColor: 'green',
        // backgroundSelectedGradient: {
        //     type: 'linear',
        //     colors: ['#333', 'transparent'],
        //     startPoint: {
        //         x: 0,
        //         y: 0
        //     },
        //     endPoint: {
        //         x: 0,
        //         y: "100%"
        //     }
        // },
        // verticalAlign:'bottom',
        text: 'This is a sample\n text for a label'
    });
    // label.add(Ti.UI.createView({
    //     touchEnabled: false,
    //     backgroundColor: 'red',
    //     backgroundSelectedColor: 'white',
    //     left: 10,
    //     width: 15,
    //     height: 15
    // }));
    // label.add(Ti.UI.createView({
    //     backgroundColor: 'green',
    //     bottom: 10,
    //     width: 15,
    //     height: 15
    // }));
    // label.add(Ti.UI.createView({
    //     backgroundColor: 'yellow',
    //     top: 10,
    //     width: 15,
    //     height: 15
    // }));
    // label.add(Ti.UI.createView({
    //     backgroundColor: 'orange',
    //     right: 10,
    //     width: 15,
    //     height: 15
    // }));
    var t3 = Ti.UI.create2DMatrix().scale(2.0, 2.0).translate(0, -40).rotate(90);
    label.addEventListener('longpress', function(e) {
        label.animate({
            duration: 5000,
            // width:'FILL',
            // height:'FILL',
            // bottom:0,
            autoreverse: true,
            fullscreen: !label.fullscreen
            // transform: varSwitch(label.transform, t3, t1),
        });
    });
    win.add(label);
    var button2 = Ti.UI.createButton({
        padding: {
            left: 80
        },
        bubbleParent: false,
        backgroundColor: 'gray',
        dispatchPressed: true,
        selectedColor: 'red',
        backgroundSelectedGradient: {
            type: 'linear',
            colors: ['#333', 'transparent'],
            startPoint: {
                x: 0,
                y: 0
            },
            endPoint: {
                x: 0,
                y: "100%"
            }
        },
        title: 'test buutton'
    });
    button2.add(Ti.UI.createButton({
        left: 0,
        backgroundColor: 'green',
        selectedColor: 'red',
        backgroundSelectedGradient: {
            type: 'linear',
            colors: ['#333', 'transparent'],
            startPoint: {
                x: 0,
                y: 0
            },
            endPoint: {
                x: 0,
                y: "100%"
            }
        },
        title: 'Osd'
    }));
    win.add(button2);
    openWin(win);
}

function pullToRefresh() {
    var win = createWin();
    var sections = [];

    var fruitSection = Ti.UI.createListSection({
        headerTitle: 'Fruits'
    });
    var fruitDataSet = [{
        properties: {
            title: 'Apple'
        }
    }, {
        properties: {
            title: 'Banana'
        }
    }, {
        properties: {
            title: 'Cantaloupe'
        }
    }, {
        properties: {
            title: 'Fig'
        }
    }, {
        properties: {
            title: 'Guava'
        }
    }, {
        properties: {
            title: 'Kiwi'
        }
    }];
    fruitSection.setItems(fruitDataSet);
    sections.push(fruitSection);

    var header = new Label({
        properties: {
            width: 'FILL',
            textAlign: 'left',
            text: 'Vegetables'
        },
        childTemplates: [{
            type: 'Ti.UI.Switch',
            bindId: 'switch',
            properties: {
                right: 0
            },
            events: {
                'change': function() {
                    vegSection.visible = !vegSection.visible;
                }
            }
        }]
    });
    var vegSection = Ti.UI.createListSection({
        headerView: header
    });
    var vegDataSet = [{
        properties: {
            title: 'Carrots'
        }
    }, {
        properties: {
            title: 'Potatoes'
        }
    }, {
        properties: {
            title: 'Corn'
        }
    }, {
        properties: {
            title: 'Beans'
        }
    }, {
        properties: {
            title: 'Tomato'
        }
    }];
    vegSection.setItems(vegDataSet);

    var fishSection = Ti.UI.createListSection({
        headerTitle: 'Fish'
    });
    var fishDataSet = [{
        properties: {
            title: 'Cod'
        }
    }, {
        properties: {
            title: 'Haddock'
        }
    }, {
        properties: {
            title: 'Salmon'
        }
    }, {
        properties: {
            title: 'Tuna'
        }
    }];
    fishSection.setItems(fishDataSet);

    var refreshCount = 0;

    function loadTableData() {
        if (refreshCount == 0) {
            listView.appendSection(vegSection);
        } else if (refreshCount == 1) {
            listView.appendSection(fishSection);
        }
        refreshCount++;
        listView.closePullView();
    }
    var pullToRefresh = ak.ti.createFromConstructor('PullToRefresh', {
        rclass: 'NZBPTR'
    });
    var listView = Ti.UI.createListView({
        height: '90%',
        top: 0,
        rowHeight: 50,
        sections: sections,
        pullView: pullToRefresh
    });
    listView.add({
        type: 'Ti.UI.ActivityIndicator',
        // properties: {
        backgroundColor: 'purple',
        width: 60,
        height: 60
        // }
    });

    // listView.add({
    // bindId: 'testLabel',
    // properties: {
    // height: 50
    // },
    // childTemplates: [{
    // type: 'Ti.UI.View',
    // properties: {
    // width: 'SIZE',
    // height: 'SIZE',
    // layout: 'horizontal',
    // backgroundColor: 'red'
    // },
    // childTemplates: [{
    // bindId: 'arrow',
    // type: 'Ti.UI.Label',
    // properties: {
    // backgroundColor: 'green',
    // font: {
    // size: 14,
    // weight: 'bold'
    // },
    // shadowColor: 'white',
    // shadowRadius: 2,
    // shadowOffset: {
    // x: -10,
    // y: 1
    // },
    // textAlign: 'center',
    // color: '#3A87AD',
    // text: 'a'
    // },
    // }, {
    // bindId: 'label',
    // type: 'Ti.UI.Label',
    // properties: {
    // font: {
    // size: 14,
    // weight: 'bold'
    // },
    // shadowColor: 'white',
    // shadowRadius: 2,
    // shadowOffset: {
    // x: -10,
    // y: 1
    // },
    // textAlign: 'center',
    // color: '#3A87AD',
    // text: 'Pull down to refresh...',
    // backgroundColor: 'blue'
    // },
    // }]
    // }]
    // });
    // listView.testLabel.addEventListener('click', function() {
    // listView.arrow.hide();
    // listView.label.text = 'Loading ...';
    // });
    pullToRefresh.setListView(listView);
    pullToRefresh.addEventListener('pulled', function() {
        listView.showPullView();
        setTimeout(loadTableData, 4000);
    });
    win.add(listView);
    openWin(win);
}

function maskEx() {
    var win = createWin();
    win.backgroundGradient = {
        type: 'linear',
        colors: ['gray', 'white'],
        startPoint: {
            x: 0,
            y: 0
        },
        endPoint: {
            x: 0,
            y: "100%"
        }
    };
    var view = Ti.UI.createView({
        top: 20,
        borderRadius: 10,
        borderColor: 'red',
        borderWidth: 5,
        bubbleParent: false,
        width: 300,
        height: 100,
        backgroundColor: 'green',
        viewMask: '/images/body-mask.png',
        backgroundGradient: {
            type: 'linear',
            colors: ['red', 'green', 'orange'],
            startPoint: {
                x: 0,
                y: 0
            },
            endPoint: {
                x: 0,
                y: "100%"
            }
        }
    });
    var imageView = Ti.UI.createImageView({
        bottom: 20,
        // borderRadius : 10,
        // borderColor:'red',
        // borderWidth:5,
        bubbleParent: false,
        width: 300,
        height: 100,
        backgroundColor: 'yellow',
        scaleType: Ti.UI.SCALE_TYPE_ASPECT_FIT,
        image: '/images/slightlylargerimage.png',
        imageMask: '/images/body-mask.png',
        // viewMask : '/images/mask.png',
    });
    view.add(Ti.UI.createView({
        backgroundColor: 'red',
        bottom: 10,
        width: 30,
        height: 30
    }));
    win.add(view);
    win.add(imageView);
    win.add(Ti.UI.createButton({
        borderRadius: 20,
        padding: {
            left: 30,
            top: 30,
            bottom: 30,
            right: 30
        },
        bubbleParent: false,
        title: 'test buutton',
        viewMask: '/images/body-mask.png'
    }));
    openWin(win);
}

function ImageViewEx() {
    var win = createWin();
    win.add({
        type: 'Ti.UI.ScrollView',
        properties: {
            layout: 'vertical',
            backgroundColor: 'green',
            width: 'FILL',
            height: 'FILL',
        },
        childTemplates: [{
            type: 'Ti.UI.ImageView',
            properties: {
                width: 'FILL',
                backgroundColor: 'red',
                scaleType: Ti.UI.SCALE_TYPE_ASPECT_FILL,
                top: -20,
                height: 'SIZE',
                image: '/images/login_logo.png'
            }
        }]
    });
    // var view = Ti.UI.createImageView({
    // width:'FILL',
    // backgroundColor:'red',
    // scaleType:Ti.UI.SCALE_TYPE_ASPECT_FILL,
    // top:-20,
    // height:'SIZE',
    // image:'/images/login_logo.png'
    // });
    // view.add(Ti.UI.createView({
    // backgroundColor: 'yellow',
    // top: 10,
    // width: 15,
    // height: 15
    // }));
    // view.addEventListener('click', function() {
    // //		view.image = varSwitch(view.image, '/images/slightlylargerimage.png', '/images/poster.jpg');
    // view.animate({
    // width: 'FILL',
    // height: 'FILL',
    // duration: 1000,
    // autoreverse: true
    // });
    // });
    // win.add(view);
    openWin(win);
}

function random(min, max) {
    if (max == null) {
        max = min;
        min = 0;
    }
    return min + Math.floor(Math.random() * (max - min + 1));
};

function scrollableEx() {
    var win = createWin();
    // Create a custom template that displays an image on the left,
    // then a title next to it with a subtitle below it.
    var myTemplate = {
        properties: {
            height: 50
        },
        childTemplates: [{
            type: 'Ti.UI.ImageView',
            bindId: 'leftImageView',
            properties: {
                left: 0,
                width: 40,
                localLoadSync: true,
                height: 40,
                transform: Ti.UI.create2DMatrix().rotate(90),
                backgroundColor: 'blue',
                // backgroundSelectedColor:'green',
                image: '/images/contactIcon.png',
                // borderColor:'red',
                // borderWidth:2
                viewMask: '/images/contactMask.png',
            }
        }, {
            type: 'Ti.UI.Label',
            bindId: 'label',
            properties: {
                multiLineEllipsize: Ti.UI.TEXT_ELLIPSIZE_TAIL,
                top: 2,
                bottom: 2,
                left: 45,
                padding: {
                    bottom: 4
                },
                right: 55,
                touchEnabled: false,
                height: Ti.UI.FILL,
                color: 'black',
                font: {
                    size: 16
                },
                width: Ti.UI.FILL
            }
        }, {
            type: 'Ti.UI.ImageView',
            bindId: 'rightImageView',
            properties: {
                right: 5,
                top: 8,
                localLoadSync: true,
                bottom: 8,
                width: Ti.UI.SIZE,
                touchEnabled: false
            }
        }, {
            type: 'Ti.UI.ImageView',
            bindId: 'networkIndicator',
            properties: {
                right: 40,
                top: 4,
                localLoadSync: true,
                height: 15,
                width: Ti.UI.SIZE,
                touchEnabled: false
            }
        }, {
            type: 'Ti.UI.View',
            properties: {
                backgroundColor: '#999',
                left: 4,
                right: 4,
                bottom: 0,
                height: 1
            }
        }]
    };
    var contactAction;
    var blurImage;
    var listView = Ti.UI.createListView({
        // Maps myTemplate dictionary to 'template' string
        templates: {
            'template': myTemplate
        },
        defaultItemTemplate: 'template',
        selectedBackgroundGradient: {
            type: 'linear',
            colors: ['blue', 'green'],
            startPoint: {
                x: 0,
                y: 0
            },
            endPoint: {
                x: 0,
                y: "100%"
            }
        }
    });
    listView.addEventListener('itemclick', function(_event) {
        if (_event.hasOwnProperty('section') && _event.hasOwnProperty('itemIndex')) {
            var item = _event.section.getItemAt(_event.itemIndex);
            if (!contactAction) {
                contactAction = Ti.UI.createView({
                    backgroundColor: 'green'
                });
                blurImage = Ti.UI.createImageView({
                    scaleType: Ti.UI.SCALE_TYPE_ASPECT_FILL,
                    width: Ti.UI.FILL,
                    height: Ti.UI.FILL
                });
                contactAction.add(blurImage);
                blurImage.addEventListener('click', function() {
                    animation.fadeOut(contactAction, 200, function() {
                        win.remove(contactAction);
                    });
                });
            }
            contactAction.opacity = 0;
            win.add(contactAction);
            var image = Ti.Media.takeScreenshot();
            // var image = Ti.Image.getFilteredViewToImage(win, Ti.Image.FILTER_GAUSSIAN_BLUR, {scale:0.3});
            blurImage.image = image;
            animation.fadeIn(contactAction, 300);
        }
    });
    var names = ['Carolyn Humbert',
        'David Michaels',
        'Rebecca Thorning',
        'Joe B',
        'Phillip Craig',
        'Michelle Werner',
        'Philippe Christophe',
        'Marcus Crane',
        'Esteban Valdez',
        'Sarah Mullock'
    ];

    function formatTitle(_history) {
        return _history.fullName + '<br><small><small><b><font color="#5B5B5B">' + (new Date()).toString() +
            '</font> <font color="#3FAC53"></font></b></small></small>';
    }

    function random(min, max) {
        if (max == null) {
            max = min;
            min = 0;
        }
        return min + Math.floor(Math.random() * (max - min + 1));
    };

    function update() {
        // var outgoingImage = Ti.Utils.imageBlob('/images/outgoing.png');
        // var incomingImage = Ti.Utils.imageBlob('/images/incoming.png');
        var dataSet = [];
        for (var i = 0; i < 300; i++) {
            var callhistory = {
                fullName: names[Math.floor(Math.random() * names.length)],
                date: random(1293886884000, 1376053320000),
                kb: random(0, 100000),
                outgoing: !!random(0, 1),
                wifi: !!random(0, 1)
            };
            dataSet.push({
                contactName: callhistory.fullName,
                label: {
                    html: formatTitle(callhistory)
                },
                rightImageView: {
                    image: (callhistory.outgoing ? '/images/outgoing.png' : '/images/incoming.png')
                },
                networkIndicator: {
                    image: (callhistory.wifi ? '/images/wifi.png' : '/images/mobile.png')
                }
            });
        }
        var historySection = Ti.UI.createListSection();
        historySection.setItems(dataSet);
        listView.sections = [historySection];
    }
    win.add(listView);
    win.addEventListener('open', update);
    openWin(win);
}


function fadeInEx() {
    var win = createWin();
    var view = Ti.UI.createView({
        backgroundColor: 'red',
        opacity: 0,
        width: 200,
        height: 200
    });
    view.add(Ti.UI.createView({
        backgroundColor: 'blue',
        bottom: 10,
        width: 50,
        height: 50
    }));
    var showMe = function() {
        view.opacity = 0;
        view.transform = Ti.UI.create2DMatrix().scale(0.6, 0.6);
        win.add(view);
        view.animate({
            opacity: 1,
            duration: 300,
            transform: Ti.UI.create2DMatrix()
        });
    };
    var hideMe = function(_callback) {
        view.animate({
            opacity: 0,
            duration: 200
        }, function() {
            win.remove(view);
        });
    };
    var button = Ti.UI.createButton({
        top: 10,
        width: 100,
        bubbleParent: false,
        title: 'test buutton'
    });
    button.addEventListener('click', function(e) {
        if (view.opacity === 0) showMe();
        else hideMe();
    });
    win.add(button);
    openWin(win);
}
// if (false) {
// 	var set = Ti.UI.createAnimationSet({
// 		playMode : 1
// 	});
// 	set.addEventListener('complete', function(e) {
// 		gone = true;
// 	});
// 	set.add({
// 		duration : 800,
// 		transform : t1
// 	}, view);
// 	set.add({
// 		duration : 800,
// 		transform : t2
// 	}, view);
// 	set.add({
// 		duration : 500,
// 		transform : t3
// 	}, view);
// 	set.add({
// 		duration : 500,
// 		transform : t4
// 	}, view);
// 	view.addEventListener('click', function(e) {
// 		if (gone === true) {
// 			view.animate({
// 				duration : 300,
// 				transform : t0
// 			}, function() {
// 				gone = false
// 			});
// 		} else
// 			set.start();
// 	});
// 	win.addEventListener('click', function(e) {
// 		if (gone === true) {
// 			view.animate({
// 				duration : 300,
// 				transform : t0
// 			}, function() {
// 				gone = false
// 			});
// 		}
// 	});
// } else {
// function transform2Ex() {
// 	var win = createWin();
// 	var view = app.modules.shapes.createView({
// 		top : 150,
// 		borderRadius : 10,
// 		borderColor : 'red',
// 		borderWidth : 5,
// 		bubbleParent : false,
// 		width : 300,
// 		height : 100,
// 		backgroundColor : 'white',
// 		transform : Ti.UI.create2DMatrix().scale(2.0, 2.0),
// 		viewMask : '/images/body-mask.png'
// 	});
// 	var button = Ti.UI.createButton({
// 		top : 10,
// 		width : 100,
// 		transform : Ti.UI.create2DMatrix().scale(2.0, 2.0).translate(0, 40),
// 		bubbleParent : false,
// 		title : 'test buutton'
// 	});
// 	button.addEventListener('click', function(e) {
// 		view.top -=1;
// 	});
// 	button.add(Ti.UI.createView({
// 		backgroundColor : 'red',
// 		bottom : 10,
// 		width : 5,
// 		height : 5
// 	}));
// 	var shape = app.modules.shapes.createCircle({
// 		fillColor : '#bbb',
// 		lineColor : '#777',
// 		lineWidth : 1,
// 		lineShadow : {
// 			radius : 2,
// 			color : 'black'
// 		},
// 		radius : '45%'
// 	});
// 	view.add(shape);
// 	view.add(Ti.UI.createView({
// 		backgroundColor : 'red',
// 		bottom : 10,
// 		width : 30,
// 		height : 30
// 	}));
// 	view.addEventListener('click', function(e) {
// 		if (__ANDROID__)
// 			set.cancel();
// 		app.modules.shapes.animate({
// 			duration : 400,
// 			lineWidth : 20,
// 			autoreverse : true,
// 			lineColor : 'yellow',
// 			fillColor : 'blue'
// 		});
// 	});
// 	win.add(view);
// 	win.add(button);
// 	if (__ANDROID__) {
// 		var set = Ti.UI.createAnimationSet({
// 			playMode : 2
// 		});
// 		set.add({
// 			duration : 300,
// 			autoreverse : true,
// 			height : 300
// 		}, view);
// 		set.add({
// 			duration : 1000,
// 			lineWidth : 20,
// 			autoreverse : true,
// 			lineColor : 'yellow',
// 			fillColor : 'blue'
// 		}, shape);
// 		win.addEventListener('click', function(e) {
// 			app.modules.shapes.cancelAllAnimations();
// 			set.start();
// 		});
// 	}
// 	else {
// 		win.addEventListener('click', function(e) {
// 			view.animate({
// 				duration : 300,
// 				autoreverse : true,
// 				height : 300
// 			});
// 		});
// 	}
// 	openWin(win);
// }

function htmlLabelEx() {
    var win = createWin();
    var scrollView = Ti.UI.createScrollView({
        layout: 'vertical',
        contentWidth: 'FILL',
        contentHeight: Ti.UI.SIZE
    });
    scrollView.add(Ti.UI.createLabel({
        width: Ti.UI.FILL,
        padding: {
            left: 20,
            right: 20,
            top: 20,
            bottom: 20
        },
        height: Ti.UI.SIZE,
        ellipsize: Ti.UI.TEXT_ELLIPSIZE_TAIL,
        maxHeight: 100,
        bottom: 20,
        html: html
    }));
    scrollView.add(Ti.UI.createLabel({
        multiLineEllipsize: Ti.UI.TEXT_ELLIPSIZE_HEAD,
        truncationString: '_ _',
        // verticalAlign:'top',
        bottom: 20,
        html: html
    }));
    scrollView.add(Ti.UI.createLabel({
        multiLineEllipsize: Ti.UI.TEXT_ELLIPSIZE_MIDDLE,
        bottom: 20,
        html: html
    }));
    scrollView.add(Ti.UI.createLabel({
        width: Ti.UI.FILL,
        height: Ti.UI.SIZE,
        // verticalAlign:'bottom',
        bottom: 20,
        multiLineEllipsize: Ti.UI.TEXT_ELLIPSIZE_TAIL,
        html: html
    }));
    scrollView.add(Ti.UI.createLabel({
        width: 200,
        height: Ti.UI.SIZE,
        backgorundColor: 'green',
        bottom: 20,
        multiLineEllipsize: Ti.UI.TEXT_ELLIPSIZE_TAIL,
        html: html
    }));
    scrollView.add(Ti.UI.createLabel({
        width: 200,
        height: Ti.UI.SIZE,
        backgorundColor: 'blue',
        bottom: 20,
        ellipsize: Ti.UI.TEXT_ELLIPSIZE_TAIL,
        html: html
    }));
    scrollView.add(Ti.UI.createLabel({
        height: 200,
        bottom: 20,
        ellipsize: Ti.UI.TEXT_ELLIPSIZE_TAIL,
        html: html
    }));
    win.add(scrollView);
    scrollView.addEventListener('click', function(e) {
        Ti.API.info(e.link);
    })

    openWin(win);
}

function svgExs() {
    var win = createWin();
    var listview = createListView();
    listview.sections = [{
        items: [{
            properties: {
                title: 'View'
            },
            callback: svg1Ex
        }, {
            properties: {
                title: 'Button'
            },
            callback: svg2Ex
        }, {
            properties: {
                title: 'ImageView'
            },
            callback: svg3Ex
        }, {
            properties: {
                title: 'ListView'
            },
            callback: svg4Ex
        }]
    }];
    win.add(listview);
    openWin(win);
}

function svg1Ex() {
    var win = createWin();
    var view = Ti.UI.createView({
        bubbleParent: false,
        width: 100,
        height: 100,
        backgroundColor: 'yellow',
        scaleType: Ti.UI.SCALE_TYPE_ASPECT_FIT,
        preventDefaultImage: true,
        backgroundImage: '/images/Notepad_icon_small.svg'
    });
    win.add(view);
    var button = Ti.UI.createButton({
        top: 20,
        bubbleParent: false,
        title: 'change svg'
    });
    button.addEventListener('click', function() {
        view.backgroundImage = varSwitch(view.backgroundImage, '/images/gradients.svg', '/images/Logo.svg');
    });
    win.add(button);
    var button2 = Ti.UI.createButton({
        bottom: 20,
        bubbleParent: false,
        title: 'animate'
    });
    button2.addEventListener('click', function() {
        view.animate({
            height: Ti.UI.FILL,
            width: Ti.UI.FILL,
            duration: 2000,
            autoreverse: true
        });
    });
    win.add(button2);
    openWin(win);
}

function svg2Ex() {
    var win = createWin();
    var button = Ti.UI.createButton({
        top: 20,
        padding: {
            left: 30,
            top: 30,
            bottom: 30,
            right: 30
        },
        bubbleParent: false,
        image: '/images/Logo.svg',
        title: 'test buutton'
    });
    win.add(button);
    openWin(win);
}

function svg3Ex() {
    var win = createWin({
        backgroundImage: '/images/Notepad_icon_small.svg'
    });
    var imageView = Ti.UI.createImageView({
        bubbleParent: false,
        width: 300,
        height: 100,
        backgroundColor: 'yellow',
        scaleType: Ti.UI.SCALE_TYPE_ASPECT_FIT,
        preventDefaultImage: true,
        image: '/images/Notepad_icon_small.svg'
    });
    imageView.addEventListener('click', function() {
        imageView.scaleType = (imageView.scaleType + 1) % 6;
    });
    win.add(imageView);
    var button = Ti.UI.createButton({
        top: 20,
        bubbleParent: false,
        title: 'change svg'
    });
    button.addEventListener('click', function() {
        imageView.image = varSwitch(imageView.image, '/images/gradients.svg', '/images/Logo.svg');
    });
    win.add(button);
    var button2 = Ti.UI.createButton({
        bottom: 20,
        bubbleParent: false,
        title: 'animate'
    });
    button2.addEventListener('click', function() {
        imageView.animate({
            height: 400,
            duration: 1000,
            autoreverse: true
        });
    });
    win.add(button2);
    openWin(win);
}

function svg4Ex() {
    var win = createWin();
    var myTemplate = {
        childTemplates: [{
            type: 'Ti.UI.View',
            bindId: 'holder',
            properties: {
                width: Ti.UI.FILL,
                height: Ti.UI.FILL,
                touchEnabled: false,
                layout: 'horizontal',
                horizontalWrap: false
            },
            childTemplates: [{
                type: 'Ti.UI.ImageView',
                bindId: 'pic',
                properties: {
                    touchEnabled: false,
                    // localLoadSync:true,
                    transition: {
                        style: Ti.UI.TransitionStyle.FADE
                    },
                    height: 'FILL',
                    image: '/images/gradients.svg'
                }
            }, {
                type: 'Ti.UI.Label',
                bindId: 'info',
                properties: {
                    color: textColor,
                    touchEnabled: false,
                    font: {
                        size: 20,
                        weight: 'bold'
                    },
                    width: Ti.UI.FILL,
                    left: 10
                }
            }, {
                type: 'Ti.UI.Button',
                bindId: 'button',
                properties: {
                    title: 'menu',
                    left: 10
                }
            }]
        }, {
            type: 'Ti.UI.Label',
            bindId: 'menu',
            properties: {
                color: 'white',
                text: 'I am the menu',
                backgroundColor: '#444',
                width: Ti.UI.FILL,
                height: Ti.UI.FILL,
                opacity: 0
            },
        }]
    };
    var listView = createListView({
        templates: {
            'template': myTemplate
        },
        defaultItemTemplate: 'template'
    });
    var sections = [{
        headerTitle: 'Fruits / Frutas',
        items: [{
            info: {
                text: 'Apple'
            }
        }, {
            properties: {
                backgroundColor: 'red'
            },
            info: {
                text: 'Banana'
            },
            pic: {
                image: 'banana.png'
            }
        }]
    }, {
        headerTitle: 'Vegetables / Verduras',
        items: [{
            info: {
                text: 'Carrot'
            }
        }, {
            info: {
                text: 'Potato'
            }
        }, {
            info: {
                text: 'Potato'
            },
            pic: {
                image: '/images/opacity.svg'
            }
        }, {
            info: {
                text: 'Potato'
            }
        }, {
            info: {
                text: 'Potato'
            }
        }, {
            info: {
                text: 'Potato'
            }
        }, {
            info: {
                text: 'Potato'
            }
        }, {
            info: {
                text: 'Potato'
            }
        }, {
            info: {
                text: 'Potato'
            }
        }, {
            info: {
                text: 'Potato'
            }
        }, {
            info: {
                text: 'Potato'
            },
            pic: {
                image: '/images/opacity.svg'
            }
        }, {
            info: {
                text: 'Potato'
            }
        }, {
            info: {
                text: 'Potato'
            }
        }, {
            info: {
                text: 'Potato'
            }
        }, {
            info: {
                text: 'Potato'
            }
        }, {
            info: {
                text: 'Potato'
            }
        }, {
            info: {
                text: 'Potato'
            }
        }, {
            info: {
                text: 'Potato'
            }
        }, {
            info: {
                text: 'Potato'
            },
            pic: {
                image: '/images/Logo.svg'
            }
        }, {
            info: {
                text: 'Potato'
            }
        }, {
            info: {
                text: 'Potato'
            }
        }, {
            info: {
                text: 'Potato'
            }
        }]
    }, {
        headerTitle: 'Grains / Granos',
        items: [{
            info: {
                text: 'Corn'
            }
        }, {
            info: {
                text: 'Rice'
            }
        }]
    }];
    listView.setSections(sections);
    win.add(listView);
    openWin(win);
}

function float2color(pr, pg, pb) {
    var color_part_dec = 255 * pr;
    var r = Number(parseInt(color_part_dec, 10)).toString(16);
    color_part_dec = 255 * pg;
    var g = Number(parseInt(color_part_dec, 10)).toString(16);
    color_part_dec = 255 * pb;
    var b = Number(parseInt(color_part_dec, 10)).toString(16);
    return "#" + r + g + b;
}

function cellColor(_index) {
    switch (_index % 4) {
        case 0: // Green
            return float2color(0.196, 0.651, 0.573);
        case 1: // Orange
            return float2color(1, 0.569, 0.349);
        case 2: // Red
            return float2color(0.949, 0.427, 0.427);
            break;
        case 3: // Blue
            return float2color(0.322, 0.639, 0.800);
            break;
        default:
            break;
    }
}
var transitionsMap = [{
    title: 'SwipFade',
    id: Ti.UI.TransitionStyle.SWIPE_FADE
}, {
    title: 'Flip',
    id: Ti.UI.TransitionStyle.FLIP
}, {
    title: 'Cube',
    id: Ti.UI.TransitionStyle.CUBE
}, {
    title: 'Fold',
    id: Ti.UI.TransitionStyle.FOLD
}, {
    title: 'Fade',
    id: Ti.UI.TransitionStyle.FADE
}, {
    title: 'Back Fade',
    id: Ti.UI.TransitionStyle.BACK_FADE
}, {
    title: 'Scale',
    id: Ti.UI.TransitionStyle.SCALE
}, {
    title: 'Push Rotate',
    id: Ti.UI.TransitionStyle.PUSH_ROTATE
}, {
    title: 'Slide',
    id: Ti.UI.TransitionStyle.SLIDE
}, {
    title: 'Modern Push',
    id: Ti.UI.TransitionStyle.MODERN_PUSH
}, {
    title: 'Ghost',
    id: Ti.UI.TransitionStyle.GHOST
}, {
    title: 'Zoom',
    id: Ti.UI.TransitionStyle.ZOOM
}, {
    title: 'SWAP',
    id: Ti.UI.TransitionStyle.SWAP
}, {
    title: 'CAROUSEL',
    id: Ti.UI.TransitionStyle.CAROUSEL
}, {
    title: 'CROSS',
    id: Ti.UI.TransitionStyle.CROSS
}, {
    title: 'GLUE',
    id: Ti.UI.TransitionStyle.GLUE
}];

function navWindowEx() {
    function close() {
        slidingMenu.close();
        slidingMenu = null;
        navWin1.close();
        navWin2.close();
        navWin1 = null;
        navWin2 = null;
    }

    function createSimulateWindow(_navWin) {
        var index = _navWin.stackSize;
        var color = cellColor(index);
        var args = merge_options(initWindowArgs, {
            title: (_navWin.title + ' / win' + (_navWin.stackSize)),
            backgroundColor: 'transparent',
            navBarHidden: false
        }, true);
        if (__ANDROID__) {
            args.activity = {
                onCreateOptionsMenu: function(e) {
                    var menu = e.menu;
                    var closeMenuItem = menu.add({
                        title: "Close",
                        showAsAction: Ti.Android.SHOW_AS_ACTION_IF_ROOM
                    });
                    closeMenuItem.addEventListener("click", function(e) {
                        newWin.close({
                            transition: {
                                duration: 300
                            }
                        });
                    });
                }
            };
        }
        var newWin = Ti.UI.createWindow(args);

        function openMe(_params) {
            Ti.API.info('openMe');
            _params.transition.duration = 3000;
            _navWin.openWindow(createSimulateWindow(_navWin), _params);
        }
        var listView = createListView({
            backgroundColor: 'transparent',
            sections: [{
                items: [{
                    properties: {
                        color: 'black',
                        title: 'Swipe',
                        backgroundColor: color
                    },
                    transition: {
                        style: Ti.UI.TransitionStyle.SWIPE
                    }
                }, {
                    properties: {
                        color: 'black',
                        title: 'SwipFade',
                        backgroundColor: color,
                        backgroundOpacity: 1.0
                    },
                    transition: {
                        style: Ti.UI.TransitionStyle.SWIPE_FADE
                    }
                }, {
                    properties: {
                        color: 'black',
                        title: 'Flip',
                        backgroundColor: color
                    },
                    transition: {
                        style: Ti.UI.TransitionStyle.FLIP
                    }
                }, {
                    properties: {
                        color: 'black',
                        title: 'Cube',
                        backgroundColor: color,
                        backgroundOpacity: 1.0
                    },
                    transition: {
                        style: Ti.UI.TransitionStyle.CUBE
                    }
                }, {
                    properties: {
                        color: 'black',
                        title: 'SwipFade FromTop',
                        backgroundColor: color
                    },
                    transition: {
                        style: Ti.UI.TransitionStyle.SWIPE_FADE,
                        substyle: Ti.UI.TransitionStyle.TOP_TO_BOTTOM
                    }
                }, {
                    properties: {
                        color: 'black',
                        title: 'Flip FromBottom',
                        backgroundColor: color,
                        backgroundOpacity: 1.0
                    },
                    transition: {
                        style: Ti.UI.TransitionStyle.FLIP,
                        substyle: Ti.UI.TransitionStyle.BOTTOM_TO_TOP
                    }
                }, {
                    properties: {
                        color: 'black',
                        title: 'Fold',
                        backgroundColor: color
                    },
                    transition: {
                        style: Ti.UI.TransitionStyle.FOLD
                    }
                }, {
                    properties: {
                        color: 'black',
                        title: 'Fade',
                        backgroundColor: color,
                        backgroundOpacity: 1.0
                    },
                    transition: {
                        style: Ti.UI.TransitionStyle.FADE
                    }
                }, {
                    properties: {
                        color: 'black',
                        title: 'Back Fade',
                        backgroundColor: color
                    },
                    transition: {
                        style: Ti.UI.TransitionStyle.BACK_FADE
                    }
                }, {
                    properties: {
                        color: 'black',
                        title: 'Scale',
                        backgroundColor: color,
                        backgroundOpacity: 1.0
                    },
                    transition: {
                        style: Ti.UI.TransitionStyle.SCALE
                    }
                }, {
                    properties: {
                        color: 'black',
                        title: 'Push Rotate',
                        backgroundColor: color
                    },
                    transition: {
                        style: Ti.UI.TransitionStyle.PUSH_ROTATE
                    }
                }, {
                    properties: {
                        color: 'black',
                        title: 'Slide',
                        backgroundColor: color,
                        backgroundOpacity: 1.0
                    },
                    transition: {
                        style: Ti.UI.TransitionStyle.SLIDE
                    }
                }, {
                    properties: {
                        color: 'black',
                        title: 'Modern Push',
                        backgroundColor: color
                    },
                    transition: {
                        style: Ti.UI.TransitionStyle.MODERN_PUSH
                    }
                }, {
                    properties: {
                        color: 'black',
                        title: 'Ghost',
                        backgroundColor: color,
                        backgroundOpacity: 1.0
                    },
                    transition: {
                        style: Ti.UI.TransitionStyle.GHOST
                    }
                }, {
                    properties: {
                        color: 'black',
                        title: 'Zoom',
                        backgroundColor: color
                    },
                    transition: {
                        style: Ti.UI.TransitionStyle.ZOOM
                    }
                }, {
                    properties: {
                        color: 'black',
                        title: 'SWAP',
                        backgroundColor: color,
                        backgroundOpacity: 1.0
                    },
                    transition: {
                        style: Ti.UI.TransitionStyle.SWAP
                    }
                }, {
                    properties: {
                        color: 'black',
                        title: 'CAROUSEL',
                        backgroundColor: color
                    },
                    transition: {
                        style: Ti.UI.TransitionStyle.CAROUSEL
                    }
                }, {
                    properties: {
                        color: 'black',
                        title: 'CROSS',
                        backgroundColor: color,
                        backgroundOpacity: 1.0
                    },
                    transition: {
                        style: Ti.UI.TransitionStyle.CROSS
                    }
                }, {
                    properties: {
                        color: 'black',
                        title: 'GLUE',
                        backgroundColor: color
                    },
                    transition: {
                        style: 40
                    }
                }, ]
            }]
        });
        listView.addEventListener('itemclick', function(_event) {
            if (_event.hasOwnProperty('section') && _event.hasOwnProperty('itemIndex')) {
                var item = _event.section.getItemAt(_event.itemIndex);
                Ti.API.info('item ' + JSON.stringify(item));
                openMe({
                    transition: item.transition
                });
            }
        });
        newWin.add(listView);
        return newWin;
    }
    var navWin1 = Ti.UI.createNavigationWindow({
        swipeToClose: false,
        backgroundColor: 'transparent',
        title: 'NavWindow1'
    });
    navWin1.addEventListener('androidback', function(e) {
        e.source.closeAllWindows({
            transition: {
                duration: 1000
            }
        });
    });
    navWin1.window = createSimulateWindow(navWin1);
    var navWin2 = Ti.UI.createNavigationWindow({
        backgroundColor: 'transparent',
        title: 'NavWindow2'
    });
    navWin2.window = createSimulateWindow(navWin2);
    var args = {
        backgroundColor: backColor,
        borderRadius: 20,
        title: 'TransitionWindow'
    };
    if (__ANDROID__) {
        args.barColor = 'red';
        args.activity = {
            actionBar: {
                icon: Ti.Android.R.drawable.ic_menu_preferences,
                onHomeIconItemSelected: function(e) {
                    slidingMenu.toggleLeftView();
                }
            }
        };
    }
    var transitionWindow = Ti.UI.createWindow(args);
    var transitionViewHolder = Ti.UI.createButton({
        height: 40,
        width: 200,
        borderRadius: 10,
        disableHW: true,
        backgroundColor: 'red'
    });
    var tr1 = Ti.UI.createLabel({
        text: 'I am a text!',
        color: '#fff',
        textAlign: 'center',
        backgroundColor: 'green',
        width: Ti.UI.FILL,
        height: 40,
    });
    tr1.addEventListener('click', function(e) {
        transitionViewHolder.transitionViews(tr1, tr2, {
            style: Ti.UI.TransitionStyle.FOLD,
            duration: 3000
        });
        videoOverlayTest();
    });
    var tr2 = Ti.UI.createButton({
        title: 'I am a button!',
        color: '#000',
        height: 40,
        width: 200,
        backgroundColor: 'white'
    });
    tr2.addEventListener('click', function(e) {
        transitionViewHolder.transitionViews(tr2, tr1, {
            style: Ti.UI.TransitionStyle.SWIPE
        });
    });
    transitionViewHolder.add(tr1);
    transitionWindow.add(transitionViewHolder);
    //LeftMenu
    var leftMenu = createListView({
        backgroundColor: 'transparent',
        sections: [{
            items: [{
                properties: {
                    title: 'nav1',
                    backgroundColor: 'transparent'
                },
                callback: function() {
                    slidingMenu.centerView = navWin1;
                }
            }, {
                properties: {
                    title: 'nav2',
                    backgroundColor: 'transparent'
                },
                callback: function() {
                    slidingMenu.centerView = navWin2;
                }
            }, {
                properties: {
                    title: 'Transition',
                    backgroundColor: 'transparent'
                },
                callback: function() {
                    slidingMenu.centerView = transitionWindow;
                }
            }, {
                properties: {
                    title: 'Close',
                    backgroundColor: 'transparent'
                },
                callback: close
            }]
        }]
    });
    //slidingMenu
    var slidingMenu = new SlideMenu({
        fading: 0.5,
        panningMode: app.modules.slidemenu.MENU_PANNING_BORDERS,
        orientationModes: [Ti.UI.UPSIDE_PORTRAIT,
            Ti.UI.PORTRAIT,
            Ti.UI.LANDSCAPE_RIGHT,
            Ti.UI.LANDSCAPE_LEFT
        ],
        style: 1,
        leftViewWidth: -60,
        leftViewDisplacement: 40,
        shadowWidth: 0,
        backgroundGradient: {
            type: 'linear',
            colors: ['#444154', '#a86e6a'],
            startPoint: {
                x: 0,
                y: 0
            },
            endPoint: {
                x: "100%",
                y: 0
            }
        },
        leftView: leftMenu,
        centerView: transitionWindow
    });
    slidingMenu.open();
}

function choseTransition(_view, _property) {
    var optionTitles = [];
    for (var i = 0; i < transitionsMap.length; i++) {
        optionTitles.push(transitionsMap[i].title);
    };
    optionTitles.push('Cancel');
    var opts = {
        cancel: optionTitles.length - 1,
        options: optionTitles,
        selectedIndex: transitionsMap.indexOf(_.findWhere(transitionsMap, {
            id: _view[_property].style
        })),
        title: 'Transition Style'
    };

    var dialog = Ti.UI.createOptionDialog(opts);
    dialog.addEventListener('click', function(e) {
        if (e.cancel == false) {
            _view[_property] = {
                style: transitionsMap[e.index].id
            };
        }
    });
    dialog.show();
}

function slideMenuEx() {
    var rootWindows = [];
    var otherWindows = [];

    function closeWindow(_win) {
        var lastWin = otherWindows[otherWindows.length - 1];
        if (_win === lastWin) {
            otherWindows.pop();
            var lastWin = otherWindows[otherWindows.length - 1];
            _win.animate({
                    transform: Ti.UI.create2DMatrix().translate('100%', 0),
                    duration: 400
                },
                function() {
                    _win.close();
                    _win = null;
                });
            lastWin.animate({
                transform: null,
                opacity: 1,
                duration: 400
            });
        } else {
            _win.close();
            _win = null;
        }
    };

    function openMovieWindow(_imgUrl) {
        var win = createWin({
            navBarHidden: true,
            backgroundColor: 'blue',
            transform: Ti.UI.create2DMatrix().translate('100%', 0)
        });
        var closeButton = Ti.UI.createLabel({
            text: '<-',
            color: 'white',
            textAlign: 'center',
            backgroundColor: '#77000000',
            width: 50,
            height: 40,
            top: 0,
            left: 10,
            font: {
                size: 24,
                weight: 'bold'
            }
        });
        closeButton.addEventListener('click', function(e) {
            closeWindow(win);
            win = null;
        });
        var verticalScrollView = Ti.UI.createScrollableView({
            disableBounce: true,
            layout: 'vertical',
            transition: {
                style: Ti.UI.TransitionStyle.SWIPE_FADE,
                substyle: Ti.UI.TransitionStyle.TOP_TO_BOTTOM
            }
        });
        var topView = Ti.UI.createView({
            backgroundColor: 'black'
        });
        var glassView = Ti.UI.createView({
            bottom: 0,
            height: 100
        });
        var textView = Ti.UI.createView({
            backgroundColor: '#55ffffff'
        });
        var blurImageView = Ti.UI.createImageView({
            opacity: 0,
            preventDefaultImage: true,
            scaleType: Ti.UI.SCALE_TYPE_ASPECT_FILL,
            width: Ti.UI.FILL,
            height: Ti.UI.FILL
        });
        var imageView = Ti.UI.createImageView({
            scaleType: Ti.UI.SCALE_TYPE_ASPECT_FILL,
            preventDefaultImage: true,
            opacity: 0,
            width: Ti.UI.FILL,
            height: Ti.UI.FILL,
            image: _imgUrl.replace('-138', '')
        });
        imageView.addEventListener('load', function(e) {
            // setTimeout(function(){
            Ti.Image.getFilteredViewToImage(imageView, {
                filters: [Ti.Image.FILTER_IOS_BLUR],
                // scale: 0.3,
                // radius: 1,
                callback: function(result) {
                    blurImageView.image = result.image;
                    glassView.backgroundImage = result.image.imageAsCropped(glassView.rect, {
                        scale: 0.3
                    });
                    imageView.animate({
                        opacity: 1,
                        duration: 500
                    });
                }
            });
            scrollView.addEventListener('scroll', function(e) {
                blurImageView.opacity = Math.max(0, e.currentPageAsFloat);
            });
            // }, 2000);
        });
        var scrollView = Ti.UI.createScrollableView({
            disableBounce: true
        });
        topView.add(imageView);
        topView.add(blurImageView);
        glassView.add(textView);
        topView.add(glassView);
        topView.add(scrollView);
        var leftView = Ti.UI.createView({
            backgroundColor: '#55000000'
        });
        leftView.add(Ti.UI.createLabel({
            text: 'Test Movie',
            color: backColor,
            padding: {
                left: 20,
                right: 20,
                top: 10,
                bottom: 10
            },
            backgroundColor: '#55000000',
            borderColor: 'gray',
            borderRadius: 10,
            borderWidth: 2
        }));
        var rightView = Ti.UI.createView({
            backgroundColor: '#55000000'
        });
        rightView.add(Ti.UI.createLabel({
            text: 'Test Movie',
            color: backColor,
            padding: {
                left: 20,
                right: 20,
                top: 10,
                bottom: 10
            },
            backgroundColor: '#55000000',
            borderColor: 'gray',
            borderRadius: 10,
            borderWidth: 2
        }));
        scrollView.views = [leftView, rightView];
        var view2 = Ti.UI.createView({
            backgroundColor: 'gray'
        });
        view2.add(createListView({
            top: 100,
            backgroundColor: '#666',
            pageMargin: 10,
            exclusiveTouch: true,
            bottom: 50,
            sections: [{
                items: [{
                    properties: {
                        title: 'test1'
                    },
                    callback: function() {
                        scrollView.movePrevious(false);
                    }
                }, {
                    properties: {
                        title: 'test2'
                    }
                }, {
                    properties: {
                        title: 'test2'
                    }
                }, {
                    properties: {
                        title: 'test2'
                    }
                }, {
                    properties: {
                        title: 'test2'
                    }
                }, {
                    properties: {
                        title: 'test2'
                    }
                }, {
                    properties: {
                        title: 'test2'
                    }
                }, {
                    properties: {
                        title: 'test2'
                    }
                }, {
                    properties: {
                        title: 'test2'
                    }
                }, {
                    properties: {
                        title: 'test2'
                    }
                }, {
                    properties: {
                        title: 'test2'
                    }
                }, {
                    properties: {
                        title: 'test2'
                    }
                }, {
                    properties: {
                        title: 'test2'
                    }
                }, {
                    properties: {
                        title: 'test2'
                    }
                }, {
                    properties: {
                        title: 'test2'
                    }
                }, {
                    properties: {
                        title: 'Close'
                    },
                    callback: function() {
                        slidingMenu.close();
                        slidingMenu = null;
                    }
                }]
            }]
        }));
        view2.add({
            backgroundGradient: {
                type: 'linear',
                colors: ['#333', 'transparent'],
                startPoint: {
                    x: 0,
                    y: 0
                },
                endPoint: {
                    x: 0,
                    y: "100%"
                }
            },
            height: 10,
            width: Ti.UI.FILL,
            top: 100
        });
        view2.add({
            backgroundGradient: {
                type: 'linear',
                colors: ['transparent', '#333'],
                startPoint: {
                    x: 0,
                    y: 0
                },
                endPoint: {
                    x: 0,
                    y: "100%"
                }
            },
            height: 10,
            width: Ti.UI.FILL,
            bottom: 50
        });
        verticalScrollView.views = [topView, view2];
        win.add(verticalScrollView);
        win.add(closeButton);
        var lastWin = otherWindows[otherWindows.length - 1];
        win.addEventListener('open', function(e) {
            lastWin.animate({
                transform: Ti.UI.create2DMatrix().scale(0.9),
                opacity: 0.5,
                duration: 200
            });
            e.source.animate({
                transform: Ti.UI.create2DMatrix(),
                duration: 400
            }, function() {
                Ti.API.info('test');
                imageView.image = _imgUrl.replace('-138', '');
            });
        });
        // win.addEventListener('click', function(e) {
        // 	closeWindow(e.source);
        // 	win = null;
        // });
        win.open({
            animated: false
        });
        otherWindows.push(win);
    }
    var rootWindow1 = Ti.UI.createWindow({
        navBarHidden: true,
        backgroundColor: backColor
    });

    function getScrollViewPage(_imgUrl, _title) {
        var view = Ti.UI.createView({
            opacity: 0,
            rasterize: true,
            height: Ti.UI.FILL,
            width: Ti.UI.FILL,
            backgroundColor: 'green'
            // left:'15%',
            // right:'15%'
        });
        var imageView = Ti.UI.createImageView({
            scaleType: Ti.UI.SCALE_TYPE_ASPECT_FILL,
            height: Ti.UI.FILL,
            width: Ti.UI.FILL,
            image: _imgUrl
        });
        var glassView = Ti.UI.createLabel({
            color: 'white',
            bottom: 0,
            width: Ti.UI.FILL,
            height: 80,
            text: _title
        });
        // var textView = Ti.UI.createView({backgroundColor:'#55000000'});
        imageView.addEventListener('load', function(e) {
            glassView.blurBackground('backgroundImage', {
                filters: [Ti.Image.FILTER_IOS_BLUR],
                blend: Ti.UI.BlendMode.DARKEN,
                // radius: 1,
                // scale: 0.3,
                tint: '#aaff0000',
                callback: function() {
                    view.animate({
                        opacity: 1,
                        duration: 400
                    });
                }
            });
        });
        imageView.addEventListener('click', function(e) {
            openMovieWindow(_imgUrl);
        });
        // glassView.add(textView);
        view.add(imageView);
        view.add(glassView);
        return view;
    }
    var scrollView = Ti.UI.createScrollableView({
        backgroundColor: 'blue',
        height: 300,
        width: '90%',
        transition: {
            style: Ti.UI.TransitionStyle.SWIPE_FADE
        },
        showPagingControl: true,
        disableBounce: false,
        pageWidth: '60%',
        cacheSize: 5,
        views: [getScrollViewPage('http://zapp.trakt.us/images/posters_movies/192263-138.jpg', 'The Croods'),
            getScrollViewPage('http://zapp.trakt.us/images/posters_movies/208623-138.jpg', 'This Is The End'),
            getScrollViewPage('http://zapp.trakt.us/images/posters_movies/210231-138.jpg', 'Now You See Me'),
            getScrollViewPage('http://zapp.trakt.us/images/posters_movies/176347-138.jpg', 'Into Darkness'),
            getScrollViewPage('http://zapp.trakt.us/images/posters_movies/210596-138.jpg', 'Pain And Gain')
        ]
    });
    scrollView.addEventListener('change', function(e) {
        info('pagechange' + e.currentPage);
    })
    rootWindow1.add(scrollView);

    var button = Ti.UI.createButton({
        bottom: 0,
        bubbleParent: false,
        title: 'Transition'
    });
    button.addEventListener('click', function() {
        choseTransition(scrollView, 'transition');
    });
    rootWindow1.add(button);
    rootWindows.push(rootWindow1);

    function openRootWindow(_win) {
        // if (slidingMenu.centerView !== _win) {
        slidingMenu.centerView = _win;
        slidingMenu.rightView = Ti.UI.createView({
            properties: {
                layout: 'vertical',
                backgroundColor: 'yellow'
            },
            childTemplates: [{
                type: 'Ti.UI.ListView',
                properties: {
                    backgroundGradient: {
                        type: 'linear',
                        colors: [{
                            color: '#1E232C',
                            offset: 0.0
                        }, {
                            color: 'yellow',
                            offset: 0.2
                        }, {
                            color: 'yellow',
                            offset: 0.8
                        }, {
                            color: '#1E232C',
                            offset: 1
                        }],
                        startPoint: {
                            x: 0,
                            y: 0
                        },
                        endPoint: {
                            y: 0,
                            x: "100%"
                        }
                    },
                    width: 'FILL',
                    height: 'FILL',
                    sections: [{
                        items: [{
                            properties: {
                                title: '1'
                            }
                        }, {
                            properties: {
                                title: '2',
                                accessoryType: Titanium.UI.LIST_ACCESSORY_TYPE_CHECKMARK
                            },
                        }, {
                            properties: {
                                title: '3'
                            }
                        }, {
                            properties: {
                                title: '4'
                            }
                        }, {
                            properties: {
                                title: '5'
                            }
                        }, {
                            properties: {
                                title: '6'
                            }
                        }]
                    }]
                }
            }]
        });
        slidingMenu.rightViewWidth = 60;
        slidingMenu.rightViewDisplacement = '100%';
        // }
        for (var i = 1; i < otherWindows.length; i++) {
            var win = otherWindows[i];
            if (win !== slidingMenu)
                otherWindows[i].close();
        };
        otherWindows = [slidingMenu];
    }
    var slidingMenu = new SlideMenu({
        backgroundColor: backColor,
        navBarHidden: true,
        leftViewWidth: '40%',
        backgroundColor: 'gray',
        // fading:1.0,
        leftTransition: {
            style: Ti.UI.TransitionStyle.SLIDE,
            substyle: Ti.UI.TransitionStyle.LEFT_TO_RIGHT
        }
    });
    var listview = createListView({
        backgroundColor: 'transparent',
        sections: [{
            items: [{
                properties: {
                    title: 'test1'
                },
                callback: function() {
                    openRootWindow(rootWindow1);
                }
            }, {
                properties: {
                    title: 'test2',
                    accessoryType: Titanium.UI.LIST_ACCESSORY_TYPE_CHECKMARK
                },
                callback: transform2Ex
            }, {
                properties: {
                    title: 'PopIn'
                },
                callback: transform3Ex
            }, {
                properties: {
                    title: 'SlideIn'
                },
                callback: transform4Ex
            }, {
                properties: {
                    title: 'Transition Style'
                },
                callback: function() {
                    choseTransition(slidingMenu, 'leftTransition');
                }
            }, {
                properties: {
                    title: 'Close'
                },
                callback: function() {
                    slidingMenu.close();
                    slidingMenu = otherWindows = null; //memory leak otherwise!
                }
            }]
        }]
    });
    slidingMenu.leftView = listview;
    slidingMenu.centerView = Ti.UI.createWindow({
        backgroundColor: 'red',
        navBarHidden: true
    });
    otherWindows.push(slidingMenu);
    // slidingMenu.add(Ti.UI.createView({
    // backgroundColor: 'red',
    // height: 60,
    // width: Ti.UI.FILL
    // }));
    slidingMenu.open({
        activityEnterAnimation: Ti.App.Android.R.anim.push_up_in,
        activityExitAnimation: Ti.App.Android.R.anim.identity
    });
}

function test2() {
    var win = createWin({
        modal: true
    });
    var view = Ti.UI.createView({
        top: 0,
        width: Ti.UI.FILL,
        backgroundColor: 'purple',
        height: 60
    });
    var view1 = Ti.UI.createScrollView({
        top: 0,
        backgroundColor: 'yellow',
        layout: 'vertical',
        height: Ti.UI.SIZE,
        left: 5,
        right: 5
    });
    var view2 = Ti.UI.createView({
        height: 'SIZE',
        backgroundColor: 'blue',
        layout: 'horizontal',
        width: Ti.UI.FILL
    });
    var view3 = Ti.UI.createTextField({
        value: 'This is my tutle test',
        backgroundColor: 'red',
        ellipsize: Ti.UI.TEXT_ELLIPSIZE_TAIL,
        font: {
            size: 14
        },
        width: 75,
        right: 20
    });
    var view4 = Ti.UI.createView({
        height: 'SIZE',
        backgroundColor: 'green',
        layout: 'horizontal',
        width: Ti.UI.FILL
    });
    var view5 = Ti.UI.createLabel({
        height: Ti.UI.FILL,
        text: 'button1',
        width: Ti.UI.FILL,
        left: 0,
        height: 35,
        top: 5,
        enabled: false,
        backgroundColor: 'red',
        borderColor: '#006598',
        selectedColor: 'white',
        disabledColor: 'white',
        color: '#006598',
        backgroundDisabledColor: '#006598',
        backgroundSelectedColor: '#006598'
    });
    var view6 = Ti.UI.createLabel({
        height: Ti.UI.FILL,
        text: 'button2',
        width: Ti.UI.FILL,
        left: 0,
        height: 35,
        top: 5,
        backgroundColor: 'transparent',
        borderColor: '#006598',
        selectedColor: 'white',
        disabledColor: 'white',
        color: '#006598',
        backgroundDisabledColor: '#006598',
        backgroundSelectedColor: '#006598'
    });

    view4.add([view5, view6]);
    view2.add([view3, view4]);
    view1.add(view2);
    view.add(view1);
    win.add(view);

    win.addEventListener('click', function() {
        info('click');
        notificationView.showMessage({
            message: 'test',
            level: 'error'
        });
    })
    win.open();
}



function keyboardTest() {
    var textfield = Ti.UI.createTextField();
    var dialog = Ti.UI.createAlertDialog({
        title: 'test',
        buttonNames: ['cancel', 'ok'],
        persistent: true,
        cancel: 0,
        androidView: textfield
    });
    textfield.addEventListener('change', function(e) {
        textfield.blur();
    });
    dialog.addEventListener('open', function(e) {
        textfield.focus();
    });
    dialog.addEventListener('click', function(e) {
        if (e.cancel)
            return;
    });
    dialog.addEventListener('return', function(e) {});
    dialog.show();
}

function transitionTest() {
    var win = createWin();

    var holderHolder = Ti.UI.createView({
        // clipChildren:false,
        height: 100,
        borderColor: 'green',
        width: 220,
        backgroundColor: 'green'
    });
    var transitionViewHolder = Ti.UI.createView({
        clipChildren: false,
        height: 'SIZE',
        width: 200,
        // borderRadius: 10,
        // borderColor: 'green',
        backgroundColor: 'yellow'
    });
    var tr1 = Ti.UI.createLabel({
        text: 'I am a text!',
        color: '#fff',
        textAlign: 'center',
        backgroundColor: 'green',
        // borderRadius: 10,
        width: 50,
        height: 80,
    });
    tr1.addEventListener('click', function(e) {
        Ti.API.info('click');
        transitionViewHolder.transitionViews(tr1, tr2, {
            style: Ti.UI.TransitionStyle.CUBE,
            duration: 3000,
            reverse: true
        });
    });
    var tr2 = Ti.UI.createButton({
        title: 'I am a button!',
        color: '#000',
        // borderColor:'orange',
        // borderRadius: 20,
        height: 40,
        backgroundColor: 'white'
    });
    tr2.addEventListener('click', function(e) {
        transitionViewHolder.transitionViews(tr2, tr1, {
            style: Ti.UI.TransitionStyle.SWIPE_DUAL_FADE,
        });
    });
    transitionViewHolder.add(tr1);
    holderHolder.add(transitionViewHolder);
    win.add(holderHolder);
    openWin(win);
}

function opacityTest() {
    var win = createWin({
        dispatchPressed: true,
        backgroundSelectedColor: 'green'
    });

    var image1 = Ti.UI.createImageView({
        backgroundColor: 'yellow',
        image: "animation/win_1.png"
    });
    image1.addEventListener('longpress', function() {
        image1.animate({
            // opacity: 0,
            backgroundColor: 'transparent',
            autoreverse: true,
            duration: 2000,
        });
    });

    var button = Ti.UI.createButton({
        top: 0,
        padding: {
            left: 30,
            top: 30,
            bottom: 30,
            right: 30
        },
        height: 70,
        bubbleParent: false,
        backgroundColor: 'gray',
        touchPassThrough: true,
        dispatchPressed: false,
        backgroundSelectedGradient: {
            type: 'linear',
            colors: ['#333', 'transparent'],
            startPoint: {
                x: 0,
                y: 0
            },
            endPoint: {
                x: 0,
                y: "100%"
            }
        },
        title: 'test buutton'
    });
    button.add(Ti.UI.createView({
        enabled: true,
        backgroundColor: 'purple',
        backgroundSelectedColor: 'white',
        left: 10,
        width: 15,
        height: 15
    }));
    button.add(Ti.UI.createView({
        backgroundColor: 'green',
        bottom: 10,
        width: 15,
        height: 15
    }));
    button.add(Ti.UI.createView({
        backgroundColor: 'yellow',
        top: 10,
        width: 15,
        height: 15
    }));
    button.add(Ti.UI.createView({
        touchPassThrough: true,
        backgroundColor: 'orange',
        right: 0,
        width: 35,
        height: Ti.UI.FILL
    }));
    var t1 = Ti.UI.create2DMatrix();
    var t2 = Ti.UI.create2DMatrix().scale(2.0, 2.0).translate(0, 40).rotate(90);
    button.addEventListener('longpress', function(e) {
        button.animate({
            opacity: 0,
            // autoreverse: true,
            duration: 2000,
        });
    });
    win.add(button);
    var label = Ti.UI.createLabel({
        bottom: 20,
        height: 120,
        width: 170,
        // dispatchPressed: true,
        backgroundColor: 'gray',
        backgroundSelectedColor: '#a46',
        padding: {
            left: 30,
            top: 30,
            bottom: 30,
            right: 30
        },
        bubbleParent: false,
        selectedColor: 'green',
        backgroundSelectedGradient: {
            type: 'linear',
            colors: ['#333', 'transparent'],
            startPoint: {
                x: 0,
                y: 0
            },
            endPoint: {
                x: 0,
                y: "100%"
            }
        },
        text: 'This is a sample\n text for a label'
    });
    label.add(Ti.UI.createView({
        touchEnabled: false,
        backgroundColor: 'red',
        backgroundSelectedColor: 'white',
        left: 10,
        width: 15,
        height: 15
    }));
    label.add(Ti.UI.createView({
        backgroundColor: 'green',
        bottom: 10,
        width: 15,
        height: 15
    }));
    label.add(Ti.UI.createView({
        backgroundColor: 'yellow',
        top: 10,
        width: 15,
        height: 15
    }));
    label.add(Ti.UI.createView({
        backgroundColor: 'orange',
        right: 10,
        width: 15,
        height: 15
    }));
    var t3 = Ti.UI.create2DMatrix().scale(2.0, 2.0).translate(0, -40).rotate(90);
    label.addEventListener('longpress', function(e) {
        label.animate({
            opacity: 0,
            autoreverse: true,
            duration: 2000,
        });
    });
    win.add(label);
    var button2 = Ti.UI.createButton({
        padding: {
            left: 80
        },
        bubbleParent: false,
        backgroundColor: 'gray',
        dispatchPressed: true,
        selectedColor: 'red',
        backgroundSelectedGradient: {
            type: 'linear',
            colors: ['#333', 'transparent'],
            startPoint: {
                x: 0,
                y: 0
            },
            endPoint: {
                x: 0,
                y: "100%"
            }
        },
        title: 'test buutton'
    });
    button2.add(Ti.UI.createButton({
        left: 0,
        backgroundColor: 'green',
        selectedColor: 'red',
        backgroundSelectedGradient: {
            type: 'linear',
            colors: ['#333', 'transparent'],
            startPoint: {
                x: 0,
                y: 0
            },
            endPoint: {
                x: 0,
                y: "100%"
            }
        },
        title: 'Osd'
    }));
    win.add(button2);
    win.add(image1);
    openWin(win);
}

function imageViewTests() {
    var win = createWin();
    var listview = createListView();
    listview.sections = [{
        items: [{
            properties: {
                title: 'AnimationTest'
            },
            callback: imageViewAnimationTest
        }, {
            properties: {
                title: 'TransitionTest'
            },
            callback: imageViewTransitionTest
        }]
    }];
    win.add(listview);
    openWin(win);
}

function imageViewTransitionTest() {
    var win = createWin();

    var image1 = Ti.UI.createImageView({
        backgroundColor: 'yellow',
        image: "animation/win_1.png",
        backgroundGradient: {
            type: 'linear',
            colors: ['#333', 'transparent'],
            startPoint: {
                x: 0,
                y: 0
            },
            endPoint: {
                x: 0,
                y: "100%"
            }
        },
        localLoadSync: true,

        width: 100,
        transition: {
            style: Ti.UI.TransitionStyle.FLIP,
            // substyle:Ti.UI.TransitionStyle.TOP_TO_BOTTOM
        }
    });
    image1.add(Ti.UI.createView({
        enabled: false,
        backgroundColor: 'purple',
        backgroundSelectedColor: 'white',
        left: 10,
        width: 15,
        height: 15
    }));
    win.add(image1);
    image1.addEventListener('click', function() {
        image1.image = "animation/win_" + Math.floor(Math.random() * 16 + 1) + ".png";
    });
    var button = Ti.UI.createButton({
        bottom: 0,
        bubbleParent: false,
        title: 'Transition'
    });
    button.addEventListener('click', function() {
        choseTransition(image1, 'transition');
    });
    win.add(button);
    openWin(win);
}

function imageViewAnimationTest() {
    var win = createWin();

    var image1 = Ti.UI.createImageView({
        backgroundColor: 'yellow',
        width: 100,
        transition: {
            style: Ti.UI.TransitionStyle.FADE,
        },
        image: 'http://zapp.trakt.us/images/posters_movies/192263-138.jpg',
        animatedImages: ["animation/win_1.png", "animation/win_2.png", "animation/win_3.png",
            "animation/win_4.png",
            "animation/win_5.png", "animation/win_6.png", "animation/win_7.png", "animation/win_8.png",
            "animation/win_9.png", "animation/win_10.png", "animation/win_11.png", "animation/win_12.png",
            "animation/win_13.png", "animation/win_14.png", "animation/win_15.png", "animation/win_16.png"
        ],
        duration: 100,
        viewMask: '/images/body-mask.png'
    });
    win.add(image1);
    var btnHolder = Ti.UI.createView({
        left: 0,
        layout: 'vertical',
        height: Ti.UI.SIZE,
        width: Ti.UI.SIZE,
        backgroundColor: 'green'
    });
    btnHolder.add([{
        type: 'Ti.UI.Button',
        left: 0,
        bid: 0,
        title: 'start'
    }, {
        type: 'Ti.UI.Button',
        right: 0,
        bid: 1,
        title: 'pause'
    }, {
        type: 'Ti.UI.Button',
        left: 0,
        bid: 2,
        title: 'resume'
    }, {
        type: 'Ti.UI.Button',
        right: 0,
        bid: 3,
        title: 'playpause'
    }, {
        type: 'Ti.UI.Button',
        left: 0,
        bid: 4,
        title: 'stop'
    }, {
        type: 'Ti.UI.Button',
        right: 0,
        bid: 5,
        title: 'reverse'
    }, {
        type: 'Ti.UI.Button',
        left: 0,
        bid: 6,
        title: 'autoreverse'
    }, {
        type: 'Ti.UI.Button',
        right: 0,
        bid: 7,
        title: 'transition'
    }]);
    btnHolder.addEventListener('singletap', function(e) {
        info(stringify(e));
        switch (e.source.bid) {
            case 0:
                image1.start();
                break;
            case 1:
                image1.pause();
                break;
            case 2:
                image1.resume();
                break;
            case 3:
                image1.pauseOrResume();
                break;
            case 4:
                image1.stop();
                break;
            case 5:
                image1.reverse = !image1.reverse;
                break;
            case 6:
                image1.autoreverse = !image1.autoreverse;
                break;
            case 7:
                choseTransition(image1, 'transition');
                break;

        }
    });
    win.add(btnHolder);
    openWin(win);
}

function antiAliasTest() {
    var win = createWin();
    var html =
        '  SUCCESS     <font color="red">musique</font> électronique <b><span style="background-color:green;border-color:black;border-radius:20px;border-width:1px">est un type de </span><big><big>musique</big></big> qui a <font color="green">été conçu à</font></b> partir des années<br> 1950 avec des générateurs de signaux<br> et de sons synthétiques. Avant de pouvoir être utilisée en temps réel, elle a été primitivement enregistrée sur bande magnétique, ce qui permettait aux compositeurs de manier aisément les sons, par exemple dans l\'utilisation de boucles répétitives superposées. Ses précurseurs ont pu bénéficier de studios spécialement équipés ou faisaient partie d\'institutions musicales pré-existantes. La musique pour bande de Pierre Schaeffer, également appelée musique concrète, se distingue de ce type de musique dans la mesure où son matériau primitif était constitué des sons de la vie courante. La particularité de la musique électronique de l\'époque est de n\'utiliser que des sons générés par des appareils électroniques.';
    var view = Ti.UI.createLabel({
        backgroundColor: 'blue',
        borderWidth: 4,
        html: html,
        selectedColor: 'green',
        color: 'black',
        retina: true,
        disableHW: true,
        // borderColor: 'green',
        borderRadius: [150, 50, 0, 0],
        width: 300,
        height: 300,
        backgroundColor: 'white',
        backgroundSelectedColor: 'orange',
        backgroundInnerShadows: [{
            color: 'black',
            radius: 20
        }],
        backgroundSelectedInnerShadows: [{
            offset: {
                x: 0,
                y: 15
            },
            color: 'blue',
            radius: 20
        }],
        borderSelectedGradient: sweepGradient,
        borderColor: 'blue'
    });
    view.addEventListener('longpress', function() {
        view.animate({
            transform: 's0.3',
            duration: 2000,
            autoreverse: true,
            curve: [0, 0, 1, -1.14]
        });
    });

    win.add(view);
    openWin(win);
}

var modules = ['shapes', 'maps', 'charts'];
for (var i = 0; i < modules.length; i++) {
    Ti.include(modules[i] + '.js');
};

function modulesExs(_args) {
    var win = createWin(_args);
    var listview = createListView();
    listview.sections = [{
        items: [{
            properties: {
                title: 'Maps'
            },
            callback: mapExs
        }, {
            properties: {
                title: 'Shapes'
            },
            callback: shapeExs
        }, {
            properties: {
                title: 'Charts'
            },
            callback: chartsExs
        }]
    }];
    win.add(listview);
    openWin(win);
}

Ti.include('listview.js');
var firstWindow = createWin({
    title: 'main'
});
var listview = createListView({
    headerTitle: 'Testing Title',
    // minRowHeight:100,
    // maxRowHeight:140
});
var color = cellColor(0);
listview.sections = [{
    headerView: {
        type: 'Ti.UI.Label',
        properties: {
            backgroundColor: 'red',
            bottom: 20,
            text: 'HeaderView created from Dict'
        }
    },
    items: [{
        properties: {
            title: 'Modules',
            height:100
        },
        callback: modulesExs
    }, {
        properties: {
            title: 'ListView'
        },
        callback: listViewExs
    }, {
        properties: {
            title: 'Transform',
            height: Ti.UI.FILL,
            backgroundColor: color
        },
        callback: transformExs
    }, {
        properties: {
            height: 200,
            title: 'SlideMenu'
        },
        callback: slideMenuEx
    }, {
        properties: {
            title: 'ImageView tests'
        },
        callback: imageViewTests
    }, {
        properties: {
            title: 'antiAliasTest',
            visible: true
        },
        callback: antiAliasTest
    }, {
        properties: {
            title: 'NavigationWindow'
        },
        callback: navWindowEx
    }, {
        properties: {
            title: 'Opacity'
        },
        callback: opacityTest
    }, {
        properties: {
            title: 'Layout'
        },
        callback: layoutExs
    }, {
        properties: {
            title: 'transitionTest'
        },
        callback: transitionTest
    }, {
        properties: {
            title: 'ButtonsAndLabels'
        },
        callback: buttonAndLabelEx
    }, {
        properties: {
            title: 'Mask'
        },
        callback: maskEx
    }, {
        properties: {
            title: 'ImageView'
        },
        callback: ImageViewEx
    }, {
        properties: {
            title: 'AnimationSet'
        },
        callback: transform2Ex
    }, {
        properties: {
            title: 'HTML Label'
        },
        callback: htmlLabelEx
    }, {
        properties: {
            title: 'SVG'
        },
        callback: svgExs
    }, {
        properties: {
            title: 'PullToRefresh'
        },
        callback: pullToRefresh
    }, {
        properties: {
            title: 'webView'
        },
        callback: videoOverlayTest
    }]
}];
firstWindow.add(listview);
firstWindow.addEventListener('open', function() {
    info('open');
    listview.appendSection({
        headerTitle: 'test2',
        items: [{
            title: 'test item'
        }]
    });
});
var mainWin = Ti.UI.createNavigationWindow({
    backgroundColor: backColor,
    swipeToClose: false,
    exitOnClose: true,
    window: firstWindow,
    // transition: {
    // 	style: Ti.UI.TransitionStyle.SWIPE,
    //        curve: [0.68, -0.55, 0.265, 1.55]
    // }
});
mainWin.addEventListener('openWindow', function(e) {
    Ti.API.info(e);
});
mainWin.addEventListener('closeWindow', function(e) {
    Ti.API.info(e);
});
mainWin.open();

var notificationView = new NotificationWindow();

function textFieldTest() {
    var win = createWin();
    win.add([{
        type: 'Ti.UI.TextField',
        bindId: 'textfield',
        properties: {
            top: 20,
            width: 'FILL',
            color: 'black',
            text: 'Border Padding',
            verticalAlign: 'bottom',
            borderWidth: 2,
            borderColor: 'black',
            borderSelectedColor: 'blue',
            backgroundColor: 'gray',
            returnKeyType: Ti.UI.RETURNKEY_NEXT,
            color: '#686868',
            font: {
                size: 14
            },
            top: 4,
            bottom: 4,
            padding: {
                left: 20,
                right: 20,
                bottom: 2,
                top: 2
            },
            verticalAlign: 'center',
            left: 4,
            width: 'FILL',
            height: 'SIZE',
            right: 4,
            textAlign: 'left',
            maxLines: 2,
            borderSelectedColor: '#74B9EF',
            height: 40,
            ellipsize: Ti.UI.TEXT_ELLIPSIZE_TAIL,
            rightButton: Ti.UI.createView({
                backgroundColor: 'yellow',
                top: 8,
                bottom: 8,
                right: 0,
                width: 40
            }),
            rightButtonMode: Ti.UI.INPUT_BUTTONMODE_ONFOCUS
        }
    }, {
        type: 'Ti.UI.TextField',
        properties: {
            top: 80,
            width: 'FILL',
            color: 'black',
            text: 'Border Padding',
            verticalAlign: 'bottom',
            borderWidth: 2,
            borderColor: 'black',
            borderSelectedColor: 'blue',
            backgroundColor: 'gray'
        }
    }]);
    win.addEventListener('click', function() {
        win.textfield.focus()
    })

    openWin(win);
}
// textFieldTest();

function test4() {
    var win = createWin({
        backgroundColor: 'orange',
        layout: 'vertical'
    });
    // var view = Ti.UI.createView({
    // 	height:0,
    // 	backgroundColor:'red'
    // });
    win.add(Ti.UI.createView({
        height: 100,
        backgroundColor: 'blue'
    }));
    var view1 = Ti.UI.createView({
        height: 'FILL',
        backgroundColor: 'yellow',
        backgroundGradient: {
            type: 'linear',
            colors: ['white', 'red'],
            startPoint: {
                x: 0,
                y: 0,
            },
            endPoint: {
                x: 0,
                y: "100%",
            }
        }
    });
    view1.add(Ti.UI.createView({
        height: 50,
        bottom: 0,
        backgroundColor: 'green'
    }));
    win.add(view1);

    var view2 = Ti.UI.createView({
        visible: false,
        height: 0,
        backgroundColor: 'purple'
    })
    var view3 = Ti.UI.createLabel({
        text: 'test',
        height: 60,
        backgroundColor: 'brown'
    })
    view2.add(view3);
    win.add(view2);

    win.addEventListener('click', function() {
        if (view2.visible) {
            view2.animate({
                height: 0,
                cancelRunningAnimations: true,
                duration: 4000
            }, function() {
                view2.visible = false;
            });
        } else {
            view2.visible = true;
            view2.animate({
                cancelRunningAnimations: true,
                height: 'SIZE',
                duration: 4000
            });
        }
    })
    openWin(win);
}

function borderPaddingEx() {
    var win = createWin({
        backgroundColor: 'white'
    });
    // var view = new View({
    // "type": "Ti.UI.Label",
    // "bindId": "title",
    // "properties": {
    // "rclass": "NZBGetTVRTitle",
    // text: 'downloadpath',
    // "font": {
    // "size": 14
    // },
    // "padding": {
    // "left": 4,
    // "right": 4
    // },
    // "textAlign": "right",
    // "width": 110,
    // "color": "black"
    // }
    // });
    win.add(new Label({
        properties: {
            text: 'test',
            backgroundColor: 'red',
            bottom: 10,
        },
        events: {
            'click': function() {
                info('click');
            }
        }
    }));

    // win.add([{
    // type: 'Ti.UI.View',
    // properties: {
    // top: 20,
    // width: 100,
    // height: 50,
    // backgroundColor: 'yellow',
    // },
    // childTemplates: [{
    // bindId: 'test',
    // type: 'Ti.UI.View',
    // properties: {
    // width: '50%',
    // color: 'black',
    // hint: 'Border Padding',
    // // borderWidth: 1,
    // backgroundColor: 'green',
    // borderSelectedColor: 'blue',
    // // borderSelectedColor: 'blue',
    // // borderSelectedGradient: {
    // // 	type: 'radial',
    // // 	colors: ['orange', 'yellow']
    // // },
    // // backgroundColor: '#282D34',
    // // backgroundSelectedColor: '#3A4350',
    // borderPadding: {
    // top: -1,
    // left: -2,
    // right: -2
    // },
    // left: 0,
    // height: 'FILL',
    // borderRadius: 4,
    // backgroundGradient: {
    // type: 'linear',
    // rect: {
    // x: 0,
    // y: 0,
    // width: 40,
    // height: 40
    // },
    // colors: [{
    // offset: 0,
    // color: '#26ffffff'
    // }, {
    // offset: 0.25,
    // color: '#26ffffff'
    // }, {
    // offset: 0.25,
    // color: 'transparent'
    // }, {
    // offset: 0.5,
    // color: 'transparent'
    // }, {
    // offset: 0.5,
    // color: '#26ffffff'
    // }, {
    // offset: 0.75,
    // color: '#26ffffff'
    // }, {
    // offset: 0.75,
    // color: 'transparent'
    // }, {
    // offset: 1,
    // color: 'transparent'
    // }],
    // startPoint: {
    // x: 0,
    // y: 0
    // },
    // endPoint: {
    // x: "100%",
    // y: '100%'
    // }
    // }
    // // backgroundSelectedInnerShadows:[{color:'black', radius:10}],
    // // backgroundInnerShadows:[{color:'black', radius:10}]
    // }
    // }, {
    // type: 'Ti.UI.View',
    // properties: {
    // height: 'FILL',
    // color: 'white',
    // width: 'SIZE',
    // borderColor: '#667383',
    // borderPadding: {
    // right: -1,
    // top: -1,
    // bottom: -1
    // },
    // },
    // childTemplates: [{
    // type: 'Ti.UI.Label',
    // bindId: 'test',
    // properties: {
    // borderWidth: 3,
    // borderPadding: {
    // right: -3,
    // left: -3,
    // top: -3
    // },
    // borderSelectedColor: '#047792',
    // backgroundSelectedColor: '#667383',
    // backgroundColor: 'gray',
    // font: {
    // size: 20,
    // weight: 'bold'
    // },
    // padding: {
    // left: 15,
    // right: 15
    // },
    // color: 'white',
    // disabledColor: 'white',
    // height: 'FILL',
    // callbackId: 'search',
    // right: 0,
    // text: 'Aaaa',
    // clearIcon: 'X',
    // icon: 'A',
    // transition: {
    // style: Ti.UI.TransitionStyle.FADE
    // }
    // }
    // }]
    // }],
    // events: {
    // 'longpress': function(e) {
    // info('test' + JSON.stringify(e));
    // // if (e.bindId === 'test') {
    // e.source.text = 'toto';
    // // }
    // e.source.borderColor = 'red';
    // }
    // }
    // }]);
    //
    // win.add({
    // "properties": {
    // "rclass": "GenericRow TVRow",
    // "layout": "horizontal",
    // "height": "SIZE"
    // },
    // "childTemplates": [{
    // "type": "Ti.UI.Label",
    // "bindId": "title",
    // "properties": {
    // "rclass": "NZBGetTVRTitle",
    // "font": {
    // "size": 14
    // },
    // "padding": {
    // "left": 4,
    // "right": 4,
    // "top": 10
    // },
    // text: 'downloadpath',
    // "textAlign": "right",
    // "width": 90,
    // "color": "black",
    // // "height": "FILL",
    // "verticalAlign": "top"
    // }
    // }, {
    // "type": "Ti.UI.Label",
    // "bindId": "value",
    // "properties": {
    // selectedColor: 'green',
    // html: 'A new version is available <a href="https://github.com/RuudBurger/CouchPotatoServer/compare/b468048d95216474183daafaf46a4f2bd0d7ada7...master" target="_blank"><font color="red"><b><u>see what has changed</u></b></font></a> or <a href="update">just update, gogogo!</a>',
    // // "autoLink":Ti.UI.AUTOLINK_ALL,
    // "rclass": "NZBGetTVRValue",
    // "color": "#686868",
    // "font": {
    // "size": 14
    // },
    // // transition: {
    // // style: Ti.UI.TransitionStyle.SWIPE_FADE
    // // },
    // "top": 4,
    // "bottom": 4,
    // "padding": {
    // "left": 4,
    // "right": 4,
    // "bottom": 2,
    // "top": 2
    // },
    // "verticalAlign": "middle",
    // "left": 4,
    // "width": "FILL",
    // "height": "SIZE",
    // "right": 4,
    // "textAlign": "left",
    // "maxLines": 2,
    // "ellipsize": Ti.UI.TEXT_ELLIPSIZE_TAIL,
    // "borderColor": "#eeeeee",
    // "borderRadius": 2
    // }
    // }]
    // });
    //
    // info(win.value.text);
    // var first = true;
    // win.value.addEventListener('click',function(e){
    // info(stringify(e));
    // });
    // win.value.addEventListener('longpress',function(e){
    // info(stringify(e));
    // });

    // win.add({
    // type: 'Ti.UI.View',
    // properties: {
    // width: 200,
    // height: 20
    // },
    // events:{
    // 'click':function(e){info(stringify(e));}
    // },
    // childTemplates: [{
    // borderPadding: {
    // bottom: -1
    // },
    // borderColor: 'darkGray',
    // backgroundColor: 'gray',
    // borderRadius: 4
    // }, {
    // bindId: 'progress',
    // properties: {
    // borderPadding: {
    // top: -1,
    // left: -1,
    // right: -1
    // },
    // borderColor: '#66AC66',
    // backgroundColor: '#62C462',
    // borderRadius: 4,
    // left: 0,
    // width: '50%',
    // backgroundGradient: {
    // type: 'linear',
    // rect:{x:0, y:0, width:40, height:40},
    // colors: [{
    // offset: 0,
    // color: '#26ffffff'
    // }, {
    // offset: 0.25,
    // color: '#26ffffff'
    // }, {
    // offset: 0.25,
    // color: 'transparent'
    // }, {
    // offset: 0.5,
    // color: 'transparent'
    // }, {
    // offset: 0.5,
    // color: '#26ffffff'
    // }, {
    // offset: 0.75,
    // color: '#26ffffff'
    // }, {
    // offset: 0.75,
    // color: 'transparent'
    // }, {
    // offset: 1,
    // color: 'transparent'
    // }],
    // startPoint: {
    // x: 0,
    // y: 0
    // },
    // endPoint: {
    // x: "100%",
    // y: '100%'
    // }
    // }
    // }
    // }]
    // });

    openWin(win);
}

function testMCTS() {
    var win = createWin({
        backgroundColor: 'white'
    });

    var listview = Ti.UI.createListView({
        delaysContentTouches: false,
        templates: {
            "title": {
                "childTemplates": [{
                    "properties": {
                        "rclass": "BigTitleRowLabel",
                        "font": {
                            "size": 20,
                            "weight": "bold"
                        },
                        "padding": {
                            "left": 4,
                            "top": 2,
                            "bottom": 10,
                            "right": 4
                        },
                        "color": "black"
                    },
                    "type": "Ti.UI.Label",
                    "bindId": "label"
                }],
                "properties": {
                    "height": "SIZE",
                    "rclass": "GenericRow SizeHeight"
                }
            },
            "value": {
                "childTemplates": [{
                    "properties": {
                        "rclass": "NZBGetTVRTitle",
                        "textAlign": "right",
                        "width": 90,
                        "font": {
                            "size": 14
                        },
                        "padding": {
                            "left": 4,
                            "right": 4
                        },
                        "color": "black"
                    },
                    "type": "Ti.UI.Label",
                    "bindId": "title"
                }, {
                    "properties": {
                        "color": "#686868",
                        "padding": {
                            "left": 4,
                            "top": 2,
                            "bottom": 2,
                            "right": 4
                        },
                        "ellipsize": 'END',
                        "maxLines": 2,
                        "rclass": "NZBGetTVRValue",
                        "bottom": 4,
                        "font": {
                            "size": 14
                        },
                        "verticalAlign": "middle",
                        "borderColor": "#eeeeee",
                        "textAlign": "left",
                        "right": 4,
                        "height": "SIZE",
                        "borderRadius": 2,
                        "left": 4,
                        "width": "FILL",
                        "top": 4
                    },
                    "type": "Ti.UI.Label",
                    "bindId": "value"
                }],
                "properties": {
                    "height": "SIZE",
                    "layout": "horizontal",
                    "rclass": "GenericRow TVRow"
                }
            },
            "button": {
                "childTemplates": [{
                    // "properties" : {
                    // "rclass" : "NZBGetTVRTitle",
                    // "textAlign" : "right",
                    // "width" : 90,
                    // "font" : {
                    // "size" : 14
                    // },
                    // "padding" : {
                    // "left" : 4,
                    // "right" : 4
                    // },
                    // "color" : "black"
                    // },
                    // "type" : "Ti.UI.Label",
                    // "bindId" : "title"
                    // }, {
                    "properties": {
                        "verticalAlign": "middle",
                        "colors": ["#FEFEFE", "#E3E3E3"],
                        "color": "#686868",
                        "borderWidth": 1,
                        "width": "SIZE",
                        "top": 5,
                        "font": {
                            "size": 16
                        },
                        "selectedColors": ["#F1F1F1", "#E2E2E2"],
                        "right": 4,
                        "left": 4,
                        "bottom": 5,
                        "padding": {
                            "left": 10,
                            "top": 10,
                            "bottom": 10,
                            "right": 10
                        },
                        "bindId": "value",
                        "borderColor": "#BFBFBF",
                        "textAlign": "center",
                        "backgroundSelectedGradient": {
                            "startPoint": {
                                "x": 0,
                                "y": 0
                            },
                            "type": "linear",
                            "colors": ["#F1F1F1", "#E2E2E2"],
                            "endPoint": {
                                "x": 0,
                                "y": "100%"
                            }
                        },
                        "backgroundGradient": {
                            "startPoint": {
                                "x": 0,
                                "y": 0
                            },
                            "type": "linear",
                            "colors": ["#FEFEFE", "#E3E3E3"],
                            "endPoint": {
                                "x": 0,
                                "y": "100%"
                            }
                        },
                        "borderRadius": 4,
                        "backgroundSelectedInnerShadows": [{
                            "color": "#88000000",
                            "radius": 5,
                            "offset": {
                                "x": 0,
                                "y": 5
                            }
                        }],
                        "height": "SIZE"
                    },
                    "type": "Ti.UI.Label",
                    "bindId": "value"
                }],
                "properties": {
                    "height": "SIZE",
                    "layout": "horizontal",
                    "rclass": "GenericRow TVRow"
                }
            },
            "textfield": {
                "childTemplates": [{
                    // "properties" : {
                    // "rclass" : "NZBGetTVRTitle",
                    // "textAlign" : "right",
                    // "width" : 90,
                    // "font" : {
                    // "size" : 14
                    // },
                    // "padding" : {
                    // "left" : 4,
                    // "right" : 4
                    // },
                    // "color" : "black"
                    // },
                    // "type" : "Ti.UI.Label",
                    // "bindId" : "title"
                    // }, {
                    "type": "Ti.UI.TextField",
                    "bindId": "textfield",
                    "events": {},
                    "properties": {
                        "color": "#686868",
                        "ellipsize": 'END',
                        "padding": {
                            "left": 4,
                            "top": 2,
                            "bottom": 2,
                            "right": 4
                        },
                        "backgroundColor": "white",
                        "maxLines": 2,
                        "rclass": "TVRValue NZBGetTVRTextField",
                        "font": {
                            "size": 14
                        },
                        "borderColor": "#eeeeee",
                        "bottom": 4,
                        "verticalAlign": "middle",
                        "borderSelectedColor": "#74B9EF",
                        "borderRadius": 2,
                        "height": 40,
                        "right": 4,
                        "textAlign": "left",
                        "left": 4,
                        "width": "FILL",
                        "top": 4
                    }
                }],
                "properties": {
                    "height": "SIZE",
                    "layout": "horizontal",
                    "rclass": "GenericRow TVRow"
                }
            }
        },
        sections: [{
            "items": [{
                "template": "textfield",
                "title": {
                    "text": "name"
                },
                "textfield": {
                    // "value": "The.Conjuring.2013.1080p.BluRay.DTS.x264-CyTSuNee",
                    // "text" : "The.Conjuring.2013.1080p.BluRay.DTS.x264-CyTSuNee"
                }
            }, {
                "template": "button",
                "title": {
                    "text": "priority"
                },
                "value": {
                    "callbackId": "priority",
                    "text": "normal"
                }
            }, {
                "template": "button",
                "title": {
                    "text": "category"
                },
                "value": {
                    "callbackId": "category",
                    "text": "movies"
                }
            }, {
                "template": "button",
                "value": {
                    "callbackId": "files",
                    "text": "files"
                }
            }]
        }]
    });

    listview.addEventListener('click', function(_event) {
        if (_event.bindId && _event.hasOwnProperty('section') && _event.hasOwnProperty('itemIndex')) {
            var item = _event.section.getItemAt(_event.itemIndex);
            var callbackId = item[_event.bindId].callbackId;
            if (callbackId) {
                item[_event.bindId].text = 'test';
                _event.section.updateItemAt(_event.itemIndex, item);
            }
        }
    });
    win.addEventListener('click', function(_event) {
        info('click' + _event.bindId);
        if (_event.bindId !== 'textfield') {
            win.blur();
        }
    });
    win.add(listview);
    openWin(win);
}

function test3() {
    var win = createWin();
    var viewHolder = new View({
        width: '50%',
        height: '60',
        backgroundColor: 'yellow'
    });
    var test = new ScrollView({
        properties: {
            width: 'FILL',
            height: 'SIZE',
            layout: 'vertical'
        },
        childTemplates: [{
            properties: {
                width: 'FILL',
                height: 'SIZE',
                layout: 'horizontal'
            },
            childTemplates: [{
                type: 'Ti.UI.Label',
                properties: {
                    width: 'FILL',
                    text: 'test'
                }
            }, {
                properties: {
                    layout: 'horizontal',
                    height: 'SIZE',
                    right: 5,
                    top: 10,
                    bottom: 10,
                    width: 'FILL'
                },
                childTemplates: [{
                    type: 'Ti.UI.TextField',
                    bindId: 'textfield',
                    properties: {
                        keyboardType: Ti.UI.KEYBOARD_NUMBER_PAD,
                        left: 3,
                        width: 'FILL',
                        hintText: 'none',
                        height: 40,
                        backgroundColor: 'white',
                        font: {
                            size: 14
                        },
                    }
                }, {
                    type: 'Ti.UI.Label',
                    properties: {
                        right: 0,
                        width: 'SIZE',
                        verticalAlign: 'middle',
                        height: 'FILL',
                        padding: {
                            left: 5,
                            right: 5
                        },
                        backgroundColor: '#EEEEEE',
                        text: 'KB/s',
                        font: {
                            size: 14
                        }
                    }
                }]
            }]
        }]
    });
    var visible = false;

    win.addEventListener('longpress', function() {
        if (visible) {
            viewHolder.transitionViews(test, null, {
                style: Ti.UI.TransitionStyle.FADE
            });
        } else {
            viewHolder.transitionViews(null, test, {
                style: Ti.UI.TransitionStyle.FADE
            });
        }
        visible = !visible;
    });
    // viewHolder.add(test);
    win.add(viewHolder);

    openWin(win);
}

function testLabel() {
    var win1 = Ti.UI.createWindow({
            backgroundColor: 'blue'
        }),
        win2, win3;
    var navWin1 = Ti.UI.createNavigationWindow({
        backgroundColor: 'transparent',
        title: 'NavWindow1',
        window: win1,
        transition: {
            style: Ti.UI.TransitionStyle.FADE
        }
    });
    var leftMenu = createListView({
        backgroundColor: 'transparent',
        sections: [{
            items: [{
                properties: {
                    title: 'nav1',
                    backgroundColor: 'transparent'
                },
                callback: function() {
                    navWin1.openWindow(win1);
                    slidingMenu.toggleLeftView();
                }
            }, {
                properties: {
                    title: 'nav2',
                    backgroundColor: 'transparent'
                },
                callback: function() {
                    if (!win2) {
                        win2 = Ti.UI.createWindow({
                            backgroundColor: 'red'
                        });
                    }
                    navWin1.openWindow(win2);
                    slidingMenu.toggleLeftView();
                }
            }, {
                properties: {
                    title: 'Transition',
                    backgroundColor: 'transparent'
                },
                callback: function() {
                    if (!win3) {
                        win3 = Ti.UI.createWindow({
                            backgroundColor: 'green'
                        });
                    }
                    navWin1.openWindow(win3);
                    slidingMenu.toggleLeftView();
                }
            }, {
                properties: {
                    title: 'Close',
                    backgroundColor: 'transparent'
                },
                callback: function() {
                    slidingMenu.close();
                }
            }]
        }]
    });
    //slidingMenu
    var slidingMenu = new SlideMenu({
        orientationModes: [Ti.UI.UPSIDE_PORTRAIT,
            Ti.UI.PORTRAIT,
            Ti.UI.LANDSCAPE_RIGHT,
            Ti.UI.LANDSCAPE_LEFT
        ],
        leftViewWidth: -60,
        leftViewDisplacement: 40,
        shadowWidth: 0,
        backgroundGradient: {
            type: 'linear',
            colors: ['#444154', '#a86e6a'],
            startPoint: {
                x: 0,
                y: 0
            },
            endPoint: {
                x: "100%",
                y: 0
            }
        },
        leftView: leftMenu,
        centerView: navWin1
    });
    slidingMenu.open();
}

function deepLayoutTest() {
    var win = createWin({
        dispatchPressed: true,
        layout: 'vertical'
    });
    var viewHolder = new View({
        width: 'FILL',
        height: 60,
        backgroundColor: 'yellow'
    });
    var test = new View({
        properties: {
            backgroundColor: 'green',
            right: 0,
            width: 'SIZE',
            height: 'FILL',
            layout: 'horizontal'
        },
        childTemplates: [{
            type: 'Ti.UI.View',
            properties: {
                height: 'FILL',
                width: 'SIZE',
                borderColor: '#667383',
                borderPadding: {
                    right: -1,
                    top: -1,
                    bottom: -1
                },
            },
            childTemplates: [{
                type: 'Ti.UI.TextField',
                bindId: 'searchField',
                properties: {
                    rclass: 'CPSearchField',
                    color: 'black',
                    hintColor: 'gray',
                    right: 0,
                    height: 'FILL',
                    visible: false,
                    backgroundColor: 'white',
                    borderWidth: 3,
                    borderPadding: {
                        right: -3,
                        left: -3,
                        top: -3
                    },
                    borderColor: 'red',
                    borderSelectedColor: '#04BCE6',
                    width: 'FILL',
                    hintText: 'cp.searchfieldHint',
                    padding: {
                        left: 5,
                        right: 5
                    },
                }
            }, {
                type: 'Ti.UI.Label',
                bindId: 'search',
                properties: {
                    callbackId: 'search',
                    borderWidth: 3,
                    borderPadding: {
                        right: -3,
                        left: -3,
                        top: -3
                    },
                    borderSelectedColor: '#047792',
                    backgroundSelectedColor: '#667383',
                    backgroundColor: 'gray',
                    font: {
                        size: 20,
                        weight: 'bold'
                    },
                    padding: {
                        left: 15,
                        right: 15
                    },
                    color: 'white',
                    disabledColor: 'white',
                    height: 'FILL',
                    callbackId: 'search',
                    right: 0,
                    text: 'Aaaa',
                    clearIcon: 'X',
                    icon: 'A',
                    transition: {
                        style: Ti.UI.TransitionStyle.FADE
                    }
                }
            }]
        }]
    });

    test.addEventListener('click', function(e) {
        info('test click ' + JSON.stringify(e.source));
        if (e.source.callbackId === 'search') {
            if (test.searchField.visible) {
                var searchField = test.searchField;
                searchField.value = '';
                searchField.animate({
                    width: 1,
                    opacity: 0,
                    duration: 200
                }, function() {
                    searchField.visible = false;
                });
                searchField.blur();
                searchField.fireEvent('hidding');
            } else {
                var searchField = test.searchField;
                info('showSearchField ' + searchField.callbackId);
                searchField.applyProperties({
                    value: null,
                    opacity: 0,
                    width: 1,
                    visible: true
                });
                searchField.animate({
                    width: 'FILL',
                    opacity: 1,
                    duration: 300
                }, function() {
                    searchField.focus();
                });
                searchField.fireEvent('showing');
            }
        }
    });

    viewHolder.add(test);
    var headerView = new Label({
        properties: {
            color: 'gray',
            font: {
                size: 12
            },
            backgroundColor: 'green',
            width: 'FILL',
            height: 22,
            // borderPadding:{left:-1,right:-1,top:-1},
            padding: {
                left: 40,
                top: 2,
                bottom: 2
            },
            text: 'test'
        },
        childTemplates: [{
            type: 'Ti.UI.Label',
            properties: {
                color: 'white',
                backgroundColor: '#3A87AD',
                font: {
                    size: 12
                },
                left: 10,
                height: 16,
                // borderRadius:8,
                clipChildren: false,
                verticalAlign: 'center',
                padding: {
                    left: 8,
                    right: 8,
                    top: -2
                },
                text: '2'
            }
        }, {
            type: 'Ti.UI.Switch',
            properties: {
                right: 0,
                value: false
            },
            events: {
                'change': function(e) {
                    info(stringify(e));
                    listView.sections[1].visible = e.value;
                }
            }
        }]
    });
    var section = Ti.UI.createListSection({
        headerView: headerView,
        visible: false,
        items: [{
            title: 'test1'
        }, {
            title: 'test2'
        }]
    });

    function createSoonRow(_number) {
        var template = redux.fn.style('ListItem', {
            properties: {
                layout: 'horizontal',
                horizontalWrap: true,
                height: 'SIZE'
            }
        });
        var childTemplates = [];
        var defProps = ak.ti.style({
            type: 'Ti.UI.Label',
            properties: {
                font: {
                    size: 15,
                    weight: 'normal'
                },
                padding: {
                    left: 4,
                    top: 4,
                    right: 4
                },
                verticalAlign: 'top',
                width: 80,
                height: 120,
                visible: false,
                dispatchPressed: true,
                backgroundColor: '#55000000',
                color: 'white'
            },
            childTemplates: [{
                type: 'Ti.UI.ImageView',
                properties: {
                    width: 'FILL',
                    height: 'FILL',
                    dispatchPressed: true,
                    transition: {
                        style: Ti.UI.TransitionStyle.FADE
                    },
                    scaleType: Ti.UI.SCALE_TYPE_ASPECT_FILL
                },
                childTemplates: [{
                    type: 'Ti.UI.Label',
                    properties: {
                        width: 'FILL',
                        font: {
                            size: 15,
                            weight: 'normal'
                        },
                        padding: {
                            left: 4,
                            top: 4,
                            right: 4
                        },
                        verticalAlign: 'top',
                        height: 'FILL',
                        touchPassThrough: false,
                        backgroundSelectedColor: '#99000000',
                        color: 'transparent',
                        selectedColor: 'white',
                    }
                }]
            }]
        });
        for (var i = 0; i < _number; i++) {
            var props = redux.fn.clone(defProps);
            props.properties.imageId = i;
            props.bindId = 'soonBottomLabel' + i;
            props.childTemplates[0].bindId = 'soonImage' + i;
            props.childTemplates[0].childTemplates[0].bindId = 'soonLabel' + i;
            childTemplates.push(props);
        }
        template.childTemplates = childTemplates;
        return template;
    };
    var editMode = false;

    var listView = createListView({
        height: 'FILL',
        backgroundSelectedColor: 'blue',
        templates: {
            "titlevalue": {
                "properties": {
                    "rclass": "GenericRow TVRow",
                    "layout": "horizontal",
                    "height": "SIZE"
                },
                "childTemplates": [{
                    "type": "Ti.UI.Label",
                    "bindId": "title",
                    "properties": {
                        "rclass": "NZBGetTVRTitle",
                        "font": {
                            "size": 14
                        },
                        "padding": {
                            "left": 4,
                            "right": 4,
                            "top": 5
                        },
                        "textAlign": "right",
                        "width": 90,
                        "color": "black",
                        "verticalAlign": "top",
                        top: 0
                        // "height" : "FILL"
                    }
                }, {
                    "type": "Ti.UI.Label",
                    "bindId": "value",
                    "properties": {
                        "rclass": "NZBGetTVRValue",
                        "color": "#686868",
                        "font": {
                            "size": 14
                        },
                        "top": 4,
                        "bottom": 4,
                        "padding": {
                            "left": 4,
                            "right": 4,
                            "bottom": 2,
                            "top": 2
                        },
                        "verticalAlign": "middle",
                        "left": 4,
                        "width": "FILL",
                        "height": "SIZE",
                        "right": 4,
                        "textAlign": "left",
                        "maxLines": 2,
                        "ellipsize": "END",
                        "borderColor": "#eeeeee",
                        "borderRadius": 2
                    }
                }]
            },
            "textfield": {
                "childTemplates": [{
                    type: 'Ti.UI.View',
                    properties: {
                        left: 0,
                        width: 50,
                        height: 'FILL',
                    },
                    childTemplates: [{
                        "type": "Ti.UI.Label",
                        "bindId": "check",
                        properties: {
                            visible: false,
                            disableHW: true,
                            borderRadius: 4,
                            backgroundColor: '#2C333A',
                            backgroundSelectedColor: '#414C59',
                            backgroundInnerShadows: [{
                                radius: 10,
                                color: '#1a1e22'
                            }],
                            backgroundSelectedInnerShadows: [{
                                radius: 10,
                                color: '#252A31'
                            }],
                            color: 'transparent',
                            textAlign: 'center',
                            clipChildren: false,
                            font: {
                                size: 12
                            },
                            text: 's',
                            width: 20,
                            height: 20
                        }
                    }]
                }, {
                    "type": "Ti.UI.TextField",
                    "bindId": "textfield",
                    "events": {},
                    "properties": {
                        "color": "#686868",
                        "ellipsize": 'END',
                        "padding": {
                            "left": 4,
                            "top": 2,
                            "bottom": 2,
                            "right": 4
                        },
                        "backgroundColor": "white",
                        "maxLines": 2,
                        "font": {
                            "size": 14
                        },
                        "borderColor": "#eeeeee",
                        "bottom": 4,
                        "verticalAlign": "middle",
                        "borderSelectedColor": "#74B9EF",
                        // returnKeyType: Ti.UI.RETURNKEY_NEXT,
                        "borderRadius": 2,
                        "height": 40,
                        "right": 4,
                        "textAlign": "left",
                        "left": 4,
                        "width": "FILL",
                        "top": 4
                    }
                }],
                "properties": {
                    "height": 60,
                    "layout": "horizontal",
                    backgroundColor: 'white',
                    "rclass": "GenericRow TVRow"
                }
            },
            soonRow: createSoonRow(10),
            "release": {
                "childTemplates": [{
                    "properties": {
                        "rclass": "CPMovieReleaseRowProvider",
                        "left": 3,
                        "width": "FILL",
                        "font": {
                            "size": 11
                        },
                        "color": "#B9B9BA",
                        "height": 15
                    },
                    "type": "Ti.UI.Label",
                    "bindId": "provider"
                }, {
                    "childTemplates": [{
                        "properties": {
                            "left": 3,
                            "rclass": "CPMovieReleaseRowTitle",
                            "color": "#ffffff",
                            "verticalAlign": "top",
                            "width": "FILL",
                            "ellipsize": 'END',
                            "height": "FILL",
                            "font": {
                                "size": 12
                            }
                        },
                        "type": "Ti.UI.Label",
                        "bindId": "title"
                    }],
                    "type": "Ti.UI.View",
                    "properties": {
                        "width": "FILL",
                        "layout": "horizontal",
                        "rclass": "Fill HHolder",
                        "height": "FILL"
                    }
                }, {
                    "childTemplates": [{
                        "childTemplates": [{
                            "childTemplates": [{
                                "type": "Ti.UI.Label",
                                "properties": {
                                    "text": "",
                                    "left": 3,
                                    "rid": "cpSizeIcon",
                                    "rclass": "CPMovieReleaseRowIcon",
                                    "color": "#ffffff",
                                    "width": 12,
                                    "height": "FILL",
                                    "font": {
                                        "family": "webhostinghub",
                                        "size": 11
                                    }
                                }
                            }, {
                                "bindId": "size",
                                "type": "Ti.UI.Label",
                                "properties": {
                                    "rclass": "CPMovieReleaseRowInfos",
                                    "font": {
                                        "size": 11
                                    },
                                    "color": "#B9B9BA",
                                    "height": "FILL"
                                }
                            }],
                            "type": "Ti.UI.View",
                            "properties": {
                                "width": "SIZE",
                                "layout": "horizontal",
                                "rclass": "FillHeight SizeWidth HHolder",
                                "height": "FILL"
                            }
                        }, {
                            "childTemplates": [{
                                "type": "Ti.UI.Label",
                                "properties": {
                                    "text": "",
                                    "left": 3,
                                    "rid": "cpAgeIcon",
                                    "rclass": "CPMovieReleaseRowIcon",
                                    "color": "#ffffff",
                                    "width": 12,
                                    "height": "FILL",
                                    "font": {
                                        "family": "webhostinghub",
                                        "size": 11
                                    }
                                }
                            }, {
                                "bindId": "age",
                                "type": "Ti.UI.Label",
                                "properties": {
                                    "rclass": "CPMovieReleaseRowInfos",
                                    "font": {
                                        "size": 11
                                    },
                                    "color": "#B9B9BA",
                                    "height": "FILL"
                                }
                            }],
                            "type": "Ti.UI.View",
                            "properties": {
                                "width": "SIZE",
                                "layout": "horizontal",
                                "rclass": "FillHeight SizeWidth HHolder",
                                "height": "FILL"
                            }
                        }, {
                            "childTemplates": [{
                                "type": "Ti.UI.Label",
                                "properties": {
                                    "text": "",
                                    "left": 3,
                                    "rid": "cpScoreIcon",
                                    "rclass": "CPMovieReleaseRowIcon",
                                    "color": "#ffffff",
                                    "width": 12,
                                    "height": "FILL",
                                    "font": {
                                        "family": "webhostinghub",
                                        "size": 11
                                    }
                                }
                            }, {
                                "bindId": "score",
                                "type": "Ti.UI.Label",
                                "properties": {
                                    "rclass": "CPMovieReleaseRowInfos",
                                    "font": {
                                        "size": 11
                                    },
                                    "color": "#B9B9BA",
                                    "height": "FILL"
                                }
                            }],
                            "type": "Ti.UI.View",
                            "properties": {
                                "width": "SIZE",
                                "layout": "horizontal",
                                "rclass": "FillHeight SizeWidth HHolder",
                                "height": "FILL"
                            }
                        }],
                        "type": "Ti.UI.View",
                        "bindId": "iconsHolder",
                        "properties": {
                            "layout": "horizontal",
                            "rclass": "CPMovieReleaseRowInfosHolder"
                        }
                    }, {
                        "type": "Ti.UI.View",
                        "bindId": "statusHolder",
                        "childTemplates": [{
                            "type": "Ti.UI.View",
                            "properties": {
                                "width": "FILL",
                                "height": "FILL",
                                "rclass": "Fill"
                            }
                        }, {
                            "bindId": "status0",
                            "type": "Ti.UI.Label",
                            "properties": {
                                "visible": false,
                                "backgroundColor": "#5082BC",
                                "padding": {
                                    "left": 3,
                                    "right": 3
                                },
                                "textAlign": "center",
                                "clipChildren": false,
                                "rclass": "CPMRStatus",
                                "color": "#ffffff",
                                "right": 3,
                                "borderRadius": 2,
                                "imageId": 0,
                                "font": {
                                    "size": 11
                                }
                            }
                        }],
                        "properties": {
                            "width": "FILL",
                            "rclass": "CPMRReleaseBottomLine",
                            "layout": "horizontal",
                            "visible": true,
                            "height": 20
                        }
                    }],
                    "type": "Ti.UI.View",
                    "properties": {
                        "rclass": "CPMovieReleaseBottomLine",
                        "layout": "horizontal",
                        "width": "FILL",
                        "height": 20
                    }
                }],
                "properties": {
                    "borderColor": "#667383",
                    "backgroundSelectedColor": "#110000",
                    "layout": "vertical",
                    "backgroundColor": "#77000000",
                    "borderPadding": {
                        "left": -1,
                        "top": -1,
                        "right": -1
                    },
                    "rclass": "CPMovieReleaseRow",
                    "height": 62
                }
            },

            titleTest: {
                "properties": {
                    "rclass": "NewsDetailsRow",
                    "height": "SIZE",
                    "backgroundGradient": {
                        "type": "linear",
                        "colors": ["#F7F7F7", "white"],
                        "startPoint": {
                            "x": 0,
                            "y": 0
                        },
                        "endPoint": {
                            "x": 0,
                            "y": "100%"
                        }
                    }
                },
                "childTemplates": [{
                    "type": "Ti.UI.ImageView",
                    "bindId": "image",
                    "properties": {
                        "rclass": "NewsDetailsRowImage",
                        "top": 8,
                        "backgroundColor": "#C5C5C5",
                        "left": 8,
                        "width": 60,
                        "height": 60,
                        "retina": false,
                        "localLoadSync": true,
                        "preventDefaultImage": true
                    }
                }, {
                    "type": "Ti.UI.View",
                    "properties": {
                        "rclass": "NewsDetailsRowLabelHolder",
                        "height": "SIZE",
                        "layout": "vertical",
                        "left": 76,
                        "top": 10,
                        "bottom": 10
                    },
                    "childTemplates": [{
                        "type": "Ti.UI.Label",
                        "bindId": "title",
                        "properties": {
                            "rclass": "NewsDetailsRowTitle",
                            "height": "SIZE",
                            "maxLines": 0,
                            "color": "#6B6B6B",
                            "width": "FILL",
                            "ellipsize": "END",
                            "font": {
                                "size": 14,
                                "weight": "bold"
                            }
                        }
                    }, {
                        "type": "Ti.UI.Label",
                        "bindId": "description",
                        "properties": {
                            "rclass": "NewsDetailsRowSubtitle",
                            "height": "SIZE",
                            "color": "#3F3F3F",
                            "width": "FILL",
                            "ellipsize": "END",
                            "font": {
                                "size": 12
                            }
                        }
                    }]
                }]
            }
        },
        sections: [{
                items: [{
                    template: 'textfield',
                    check: {
                        visible: editMode
                    },
                    textfield: {
                        value: ''
                    }
                }, {
                    template: 'textfield',
                    textfield: {
                        value: ''
                    }
                }, {
                    template: 'soonRow',
                    soonBottomLabel0: {
                        visible: true,
                        text: 'test'
                    },
                    soonLabel0: {
                        text: 'test'
                    },
                    soonImage0: {
                        image: 'http://zapp.trakt.us/images/posters_movies/192263-138.jpg'
                    }
                }, {
                    template: 'release',
                    title: {
                        text: 'test release'
                    },
                    iconsHolder: {
                        visible: false
                    }
                }, {
                    template: 'titleTest',
                    title: {
                        text: 'test release'
                    },
                    description: {
                        html: "<p style=\"text-align: center;\"><img src=\"https://www.yaliberty.org/sites/default/files/imagecache/fullsize/images/Bonnie_Kristian/susq.jpg\" alt=\"Susquehanna\" title=\"Susquehanna\" class=\"imagecache imagecache-fullsize\" /></p><p>Susquehanna Young Americans for Liberty had our first&nbsp;<span style=\"letter-spacing: 0px; line-height: 1.3em; word-spacing: normal;\">meeting on February 19. We introduced ourselves and discussed why we believe liberty is important, along with some&nbsp;</span><span style=\"letter-spacing: 0px; line-height: 1.3em; word-spacing: normal;\">potential recruiting options.&nbsp;</span><span style=\"letter-spacing: 0px; line-height: 1.3em; word-spacing: normal;\">We then discussed current events and how they affect liberty and how to move ahead in expanding this chapter on campus!</span></p>"
                    }
                }, {
                    template: 'titlevalue',
                    title: {
                        text: tr('category')
                    },
                    value: {
                        text: tr('nzbget.catEmpty')
                    }
                }, {
                    template: 'titleTest',
                    title: {
                        text: 'test release'
                    },
                    description: {
                        html: "<p style=\"text-align: center;\"><img src=\"https://www.yaliberty.org/sites/default/files/imagecache/fullsize/images/Bonnie_Kristian/susq.jpg\" alt=\"Susquehanna\" title=\"Susquehanna\" class=\"imagecache imagecache-fullsize\" /></p><p>Susquehanna Young Americans for Liberty had our first&nbsp;<span style=\"letter-spacing: 0px; line-height: 1.3em; word-spacing: normal;\">meeting on February 19. We introduced ourselves and discussed why we believe liberty is important, along with some&nbsp;</span><span style=\"letter-spacing: 0px; line-height: 1.3em; word-spacing: normal;\">potential recruiting options.&nbsp;</span><span style=\"letter-spacing: 0px; line-height: 1.3em; word-spacing: normal;\">We then discussed current events and how they affect liberty and how to move ahead in expanding this chapter on campus!</span></p>"
                    }
                }, {
                    template: 'titleTest',
                    title: {
                        text: 'test release'
                    },
                    description: {
                        html: "<p style=\"text-align: center;\"><img src=\"https://www.yaliberty.org/sites/default/files/imagecache/fullsize/images/Bonnie_Kristian/susq.jpg\" alt=\"Susquehanna\" title=\"Susquehanna\" class=\"imagecache imagecache-fullsize\" /></p><p>Susquehanna Young Americans for Liberty had our first&nbsp;<span style=\"letter-spacing: 0px; line-height: 1.3em; word-spacing: normal;\">meeting on February 19. We introduced ourselves and discussed why we believe liberty is important, along with some&nbsp;</span><span style=\"letter-spacing: 0px; line-height: 1.3em; word-spacing: normal;\">potential recruiting options.&nbsp;</span><span style=\"letter-spacing: 0px; line-height: 1.3em; word-spacing: normal;\">We then discussed current events and how they affect liberty and how to move ahead in expanding this chapter on campus!</span></p>"
                    }
                }, {
                    template: 'titleTest',
                    title: {
                        text: 'test release'
                    },
                    description: {
                        html: "<p style=\"text-align: center;\"><img src=\"https://www.yaliberty.org/sites/default/files/imagecache/fullsize/images/Bonnie_Kristian/susq.jpg\" alt=\"Susquehanna\" title=\"Susquehanna\" class=\"imagecache imagecache-fullsize\" /></p><p>Susquehanna Young Americans for Liberty had our first&nbsp;<span style=\"letter-spacing: 0px; line-height: 1.3em; word-spacing: normal;\">meeting on February 19. We introduced ourselves and discussed why we believe liberty is important, along with some&nbsp;</span><span style=\"letter-spacing: 0px; line-height: 1.3em; word-spacing: normal;\">potential recruiting options.&nbsp;</span><span style=\"letter-spacing: 0px; line-height: 1.3em; word-spacing: normal;\">We then discussed current events and how they affect liberty and how to move ahead in expanding this chapter on campus!</span></p>"
                    }
                }, {
                    template: 'titleTest',
                    title: {
                        text: 'test release'
                    },
                    description: {
                        html: "<p style=\"text-align: center;\"><img src=\"https://www.yaliberty.org/sites/default/files/imagecache/fullsize/images/Bonnie_Kristian/susq.jpg\" alt=\"Susquehanna\" title=\"Susquehanna\" class=\"imagecache imagecache-fullsize\" /></p><p>Susquehanna Young Americans for Liberty had our first&nbsp;<span style=\"letter-spacing: 0px; line-height: 1.3em; word-spacing: normal;\">meeting on February 19. We introduced ourselves and discussed why we believe liberty is important, along with some&nbsp;</span><span style=\"letter-spacing: 0px; line-height: 1.3em; word-spacing: normal;\">potential recruiting options.&nbsp;</span><span style=\"letter-spacing: 0px; line-height: 1.3em; word-spacing: normal;\">We then discussed current events and how they affect liberty and how to move ahead in expanding this chapter on campus!</span></p>"
                    }
                }, {
                    template: 'titleTest',
                    title: {
                        text: 'test release'
                    },
                    description: {
                        html: "<p style=\"text-align: center;\"><img src=\"https://www.yaliberty.org/sites/default/files/imagecache/fullsize/images/Bonnie_Kristian/susq.jpg\" alt=\"Susquehanna\" title=\"Susquehanna\" class=\"imagecache imagecache-fullsize\" /></p><p>Susquehanna Young Americans for Liberty had our first&nbsp;<span style=\"letter-spacing: 0px; line-height: 1.3em; word-spacing: normal;\">meeting on February 19. We introduced ourselves and discussed why we believe liberty is important, along with some&nbsp;</span><span style=\"letter-spacing: 0px; line-height: 1.3em; word-spacing: normal;\">potential recruiting options.&nbsp;</span><span style=\"letter-spacing: 0px; line-height: 1.3em; word-spacing: normal;\">We then discussed current events and how they affect liberty and how to move ahead in expanding this chapter on campus!</span></p>"
                    }
                }, {
                    template: 'titleTest',
                    title: {
                        text: 'test release'
                    },
                    description: {
                        html: "<p style=\"text-align: center;\"><img src=\"https://www.yaliberty.org/sites/default/files/imagecache/fullsize/images/Bonnie_Kristian/susq.jpg\" alt=\"Susquehanna\" title=\"Susquehanna\" class=\"imagecache imagecache-fullsize\" /></p><p>Susquehanna Young Americans for Liberty had our first&nbsp;<span style=\"letter-spacing: 0px; line-height: 1.3em; word-spacing: normal;\">meeting on February 19. We introduced ourselves and discussed why we believe liberty is important, along with some&nbsp;</span><span style=\"letter-spacing: 0px; line-height: 1.3em; word-spacing: normal;\">potential recruiting options.&nbsp;</span><span style=\"letter-spacing: 0px; line-height: 1.3em; word-spacing: normal;\">We then discussed current events and how they affect liberty and how to move ahead in expanding this chapter on campus!</span></p>"
                    }
                }, {
                    template: 'titleTest',
                    title: {
                        text: 'test release'
                    },
                    description: {
                        html: "<p style=\"text-align: center;\"><img src=\"https://www.yaliberty.org/sites/default/files/imagecache/fullsize/images/Bonnie_Kristian/susq.jpg\" alt=\"Susquehanna\" title=\"Susquehanna\" class=\"imagecache imagecache-fullsize\" /></p><p>Susquehanna Young Americans for Liberty had our first&nbsp;<span style=\"letter-spacing: 0px; line-height: 1.3em; word-spacing: normal;\">meeting on February 19. We introduced ourselves and discussed why we believe liberty is important, along with some&nbsp;</span><span style=\"letter-spacing: 0px; line-height: 1.3em; word-spacing: normal;\">potential recruiting options.&nbsp;</span><span style=\"letter-spacing: 0px; line-height: 1.3em; word-spacing: normal;\">We then discussed current events and how they affect liberty and how to move ahead in expanding this chapter on campus!</span></p>"
                    }
                }]
            },
            section
        ]
    });

    // listView.addEventListener('click', function(e) {
    // editMode = !editMode;
    // listView.sections = [{
    // items: [{
    // template: 'textfield',
    // check: {
    // visible: editMode
    // },
    // textfield: {
    // value: ''
    // }
    // }, {
    // template: 'textfield',
    // textfield: {
    // value: ''
    // }
    // }]
    // },
    // section]
    // info('click' + e.source.backgroundSelectedColor);
    // });
    var label = new Label({
        color: '#F2F3F3',
        disabledColor: '#F2F3F3',
        width: 'FILL',
        touchPassThrough: true,
        borderWidth: 3,
        text: 'test',
        borderPadding: {
            right: -3,
            top: -3,
            bottom: -3
        },
        borderDisabledColor: 'red',
        borderSelectedColor: 'blue',
        padding: {
            left: 15,
            right: 15
        },
        font: {
            size: 20
        }
    });
    win.add(label);
    win.add(listView);
    win.add(viewHolder);
    // win.addEventListener('click', function(e){
    // // win.blur()
    // // label.enabled = !label.enabled;
    // });
    openWin(win);
}

app.utils.createNZBButton = function(_id, _rclass, _addSuffix) {
    var props = redux.fn.style('Label', {
        rclass: _rclass || 'NZBGetButton',
        rid: (_addSuffix !== false) ? (_id + 'Btn') : _id,
        bindId: _id
    });
    props.backgroundGradient = app.utils.createNZBGradient(props.colors);
    props.backgroundSelectedGradient = app.utils.createNZBGradient(props.selectedColors);
    delete props.rclass;
    delete props.rid;
    return {
        type: 'Ti.UI.Label',
        bindId: _id,
        properties: props
    };
};
app.utils.createNZBGradient = function(_colors) {
    return {
        type: 'linear',
        colors: _colors,
        startPoint: {
            x: 0,
            y: 0
        },
        endPoint: {
            x: 0,
            y: "100%"
        }
    };
};

function showSpeedLimit() {
    var viewArgs = {
        properties: {
            rclass: 'Fill SizeHeight VHolder'
        },
        childTemplates: [{
            properties: {
                rclass: 'Fill SizeHeight HHolder'
            },
            childTemplates: [{
                // type: 'Ti.UI.Label',
                // properties: {
                // rclass: 'FillWidth',
                // rid: 'nzbGetSpeedLimitDesc'
                // }
                // }, {
                properties: {
                    rid: 'nzbGetSpeedLimitTFHolder'
                },
                childTemplates: [{
                    type: 'Ti.UI.TextField',
                    bindId: 'textfield',
                    properties: {
                        rid: 'nzbGetSpeedLimitTF'
                    }
                }, {
                    type: 'Ti.UI.Label',
                    properties: {
                        // rclass: 'NZBGetBorderView',
                        rid: 'nzbGetSpeedLimitUnit'
                    }
                }]
            }]
        }]
    };
    var speedLimit = 0;
    if (speedLimit > 0) {
        viewArgs.childTemplates.push({
            type: 'Ti.UI.Label',
            properties: {
                rid: 'nzbGetCurrentSpeedLimit',
                text: 0
            }
        });
    }

    var args = {
        cancel: 0,
        // title: tr('speed_limit'),
        customView: new ScrollView(viewArgs),
        // buttonNames: ['close', 'setlimit']
    }
    var alert = new NZBGetAlert(args);
    alert.addEventListener('click', function(e) {
        if (e.cancel === false) {
            var rate = parseInt(alert.customView.textfield.value);
            Status.setSpeedLimitClick(rate, function() {
                Ti.App.fireEvent('nzbget', {
                    subtype: 'cmd',
                    command: 'rate',
                    value: rate
                });
            });

        }
    });
    app.onDebounce(alert, 'touchstart', function(e) {
        if (e.source !== alert.customView.textfield) {
            alert.customView.textfield.blur();
        }
    });

    alert.showMe();
}

function adTest() {
    var win = createWin();

    // viewHolder.add(test);
    win.add(mopub.createBannerView({
        backgroundColor: 'red'
    }));

    openWin(win);
}

var interstitial;

function adTest2() {
    interstitial = mopub.createInterstitialView({
        backgroundColor: 'red',
        adUnitId: '13260008add211e295fa123138070049'
    });
    interstitial.addEventListener('load', function() {
        interstitial.open()
    })
    interstitial.loadAd()
}

function videoOverlayTest() {
    var win = createWin({
        backgroundColor: 'transparent',
        lightweight: true,
        touchPassThrough: true,
        zIndex: 10
    });
    var htmlFormat,
        readFile = Titanium.Filesystem.getFile('video.html');
    htmlFormat = readFile.read();

    var currentDelta = 0,
        fullscreen = false,
        videoWVWidth = 200,
        videoWVRight = 20,
        totalDelta = 0,
        closeDelta = 0.5 * (videoWVWidth + videoWVRight),
        trId = Ti.UI.create2DMatrix({
            ownFrameCoord: true
        }),
        tr100 = trId.translate(2 * closeDelta, 0),
        previousX = 0,
        previousVelocity = 0,
        inAnim = {
            transform: trId,
            opacity: 1
        },
        outAnim = {
            transform: tr100,
            opacity: 0
        };

    var webView = new WebView(_.assign({
        scrollingEnabled: false,
        borderColor: 'gray',
        borderWidth: 2,
        width: videoWVWidth,
        height: Math.ceil(videoWVWidth * 9 / 16),
        bottom: videoWVRight,
        right: videoWVRight,
        backgroundColor: 'black',
        mediaPlaybackRequiresUserAction: false,
        allowsInlineMediaPlayback: true,
        pluginState: 1
    }, outAnim));

    webView.addEventListener('longpress', function(e) {
        info('longpress');
    });

    webView.addEventListener('touchstart', function(e) {
        currentDelta = 0;
        totalDelta = 0;
        previousX = e.globalPoint.x;
    });

    webView.addEventListener('touchmove', function(e) {
        var newX = previousVelocity = e.globalPoint.x - previousX;
        var newDelta = Math.max(currentDelta + newX, 0);
        var delta = newDelta - currentDelta;
        totalDelta += delta;
        currentDelta += delta;
        webView.applyProperties({
            opacity: 1 - currentDelta / (2 * closeDelta),
            transform: trId.translate(currentDelta, 0)
        });
        previousX = e.globalPoint.x;
    });

    function onTouchEnd(e) {
        var newX = e.globalPoint.x - previousX;
        var newDelta = Math.max(currentDelta + newX, 0);
        var delta = newDelta - currentDelta;
        totalDelta += delta;
        currentDelta += delta;
        info(previousVelocity);
        if (totalDelta >= closeDelta || previousVelocity >= 7) {
            webView.animate(_.assign({
                duration: totalDelta
            }, outAnim), function() {
                if (win != null) win.hideMe();
            });

        } else {
            webView.animate(_.assign({
                duration: totalDelta
            }, inAnim));
        }
    }
    webView.addEventListener('touchend', onTouchEnd);
    webView.addEventListener('touchcancel', onTouchEnd);

    win.add(webView);

    win.setVideoId = function(_videoId) {
        if (!_videoId) return;
        webView.html = htmlFormat.text.assign({
            videoId: _videoId
        });
        info(webView.html);
    };

    win.setVideoId('23m3EfXT0FM');

    var hidden = true;
    win.showMe = function() {

        if (hidden === true) {
            hidden = false;
            win.open({
                animated: false
            });
        }

        webView.animate(_.assign({
            duration: 2 * closeDelta
        }, inAnim));
    };

    win.hideMe = function() {
        if (hidden === false) {
            hidden = true;
            win.close({
                animated: false
            });
        }
    };
    win.addEventListener('close', function() {
        win = null;
    })
    win.showMe();
}

function zIndexTest() {
    var win1 = Titanium.UI.createWindow({
        title: 'Tab 1',
        backgroundColor: '#fff'
    });

    var g_backgroundGradient = {
        startPoint: {
            x: 0,
            y: 0
        },
        endPoint: {
            x: 400,
            y: 600
        },
        colors: ['blue', 'orange'],
        type: 'linear'
    };

    var backview = Titanium.UI.createView({
        backgroundGradient: g_backgroundGradient // <<< COMMENT OUT THIS LINE TO FIX PROBLEM
    });

    var view1 = Titanium.UI.createView({
        backgroundColor: 'red',
        width: '300',
        height: '400',
        left: 15,
        top: 15,
        zIndex: 10,
    });

    var view2 = Titanium.UI.createView({
        width: '100',
        height: '70',
        left: 5,
        top: 5,
        backgroundColor: 'green',
        zIndex: 1
    });
    backview.add(view1);
    backview.add(view2);

    win1.add(backview);

    win1.open();
}

function horizontalLayout() {
    var win = createWin();
    win.add({
        type: 'Ti.UI.ScrollView',
        properties: {
            width: 'FILL',
            height: 'FILL',
            layout: 'horizontal',
            horizontalWrap: true,
            backgroundColor: 'yellow'
        },
        childTemplates: [{
            // 	type: 'Ti.UI.Label',
            // 	properties: {
            // 		rid: 'dTitle'
            // 	}
            // }, {
            // 	type: 'Ti.UI.Label',
            // 	properties: {
            // 		rid: 'dDesc'
            // 	}
            // }, {
            type: 'Ti.UI.View',
            properties: {
                width: '44%',
                left: '2%',
                right: '2%',
                height: 40,
                backgroundColor: 'red'
            }
        }, {
            type: 'Ti.UI.View',
            properties: {
                width: '44%',
                left: '2%',
                right: '2%',
                height: 40,
                backgroundColor: 'blue'
            }
        }, {
            type: 'Ti.UI.View',
            properties: {
                width: '44%',
                left: '2%',
                right: '2%',
                height: 40,
                backgroundColor: 'orange'
            }
        }, {
            type: 'Ti.UI.View',
            properties: {
                width: '44%',
                left: '2%',
                right: '2%',
                height: 40,
                backgroundColor: 'purple'
            }
        }, {
            type: 'Ti.UI.TextField',
            bindId: 'userNameTF',
            properties: {
                borderRadius: 12,
                font: {
                    size: 22
                },
                backgroundColor: 'white',
                hintColor: '#8C8C8C',
                padding: {
                    left: 55,
                    right: 5
                },
                height: 50,
                width: '90%',
                bottom: 20,
                returnKeyType: Ti.UI.RETURNKEY_NEXT,
                hintText: tr('username'),
            },
            childTemplates: [{
                type: 'Ti.UI.Label',
                properties: {
                    left: 10,
                    font: {
                        size: 26
                    },
                    color: '#8C8C8C',
                    selectedColor: 'black',
                    text: 'b',
                    // focusable:false
                }
            }]
        }, {
            type: 'Ti.UI.TextField',
            bindId: 'passwordTF',
            properties: {
                borderRadius: 12,
                font: {
                    size: 22
                },
                backgroundColor: 'white',
                hintColor: '#8C8C8C',
                padding: {
                    left: 55,
                    right: 5
                },
                height: 50,
                width: '90%',
                bottom: 20,
                passwordMask: true,
                returnKeyType: Ti.UI.RETURNKEY_DONE,
                hintText: tr('password'),
            },
            childTemplates: [{
                type: 'Ti.UI.Label',
                properties: {
                    left: 10,
                    font: {
                        size: 26
                    },
                    color: '#8C8C8C',
                    selectedColor: 'black',
                    text: 'b',
                    // focusable:false
                }
            }]
        }]
    });
    openWin(win);
}

function showDummyNotification() {
    if (app.deviceinfo.__ANDROID__) {
        // Intent object to launch the application
        var intent = Ti.Android.createIntent({
            className: Ti.Android.appActivityClassName,
            action: Ti.Android.ACTION_MAIN
        });
        intent.addCategory(Ti.Android.CATEGORY_LAUNCHER);

        // Create a PendingIntent to tie together the Activity and Intent
        var pending = Ti.Android.createPendingIntent({
            intent: intent,
            flags: Ti.Android.FLAG_UPDATE_CURRENT
        });

        // Create the notification
        var notification = Ti.Android.createNotification({
            flags: Ti.Android.FLAG_SHOW_LIGHTS | Ti.Android.FLAG_AUTO_CANCEL,
            contentTitle: tr('notif_title'),
            tickerText: tr('notif_title'),
            contentText: tr('notif_desc'),
            contentIntent: pending,
            ledOnMS: 3000,
            ledARGB: 0xFFff0000
        });
        // Send the notification.
        Ti.Android.NotificationManager.notify(1234, notification);
    }
}

Ti.App.addEventListener('pause', function() {
    info('pause');
    setTimeout(showDummyNotification, 10);
});

Ti.App.addEventListener('resume', function() {
    info('resume');
});

function tabGroupExample() {
    // create tab group
    var tabGroup = Titanium.UI.createTabGroup();

    //
    // create base UI tab and root window
    //
    var win1 = Titanium.UI.createWindow({
        title: 'Tab 1',
        backgroundColor: 'blue'
    });
    var tab1 = Titanium.UI.createTab({
        icon: 'KS_nav_views.png',
        title: 'Tab 1',
        window: win1
    });

    var button = Titanium.UI.createButton({
        color: '#999',
        title: 'Show Modal Window',
        width: 180,
        height: 35
    });

    win1.add(button);
    button.addEventListener('click',
        function(e) {

            var tabWin = Titanium.UI.createWindow({
                title: 'Modal Window',
                backgroundColor: '#f0f',
                width: '100%',
                height: '100%',
                tabBarHidden: true
            });

            var tabGroup = Titanium.UI.createTabGroup({
                bottom: -500,
                width: '100%',
                height: '100%'
            });
            var tab1 = Titanium.UI.createTab({
                icon: 'KS_nav_views.png',
                width: '100%',
                height: '100%',
                title: 'tabWin',
                window: tabWin
            });
            tabGroup.addTab(tab1);
            tabGroup.open();

            var closeBtn = Titanium.UI.createButton({
                title: 'Close'
            });
            tabWin.leftNavButton = closeBtn;
            closeBtn.addEventListener('click',
                function(e) {
                    tabGroup.animate({
                            duration: 400,
                            bottom: -500
                        },
                        function() {
                            tabGroup.close()
                        });
                });

            var tBtn = Titanium.UI.createButton({
                title: 'Click For Nav Group',
                width: 180,
                height: 35
            });
            tabWin.add(tBtn);
            tBtn.addEventListener('click',
                function(e) {
                    var navWin = Titanium.UI.createWindow({
                        title: 'Nav Window',
                        backgroundColor: '#f00',
                        width: '100%',
                        height: '100%'
                    });
                    tab1.open(navWin);
                });

            tabGroup.animate({
                duration: 400,
                bottom: 0
            });
        });

    //
    // create controls tab and root window
    //
    var win2 = Titanium.UI.createWindow({
        title: 'Tab 2',
        backgroundColor: 'red'
    });

    var tBtn = Titanium.UI.createButton({
        title: 'Click For Nav Group',
        width: 180,
        height: 35
    });
    win2.add(tBtn);
    tBtn.addEventListener('click',
        function(e) {
            var navWin = Titanium.UI.createWindow({
                title: 'Nav Window',
                backgroundColor: '#f00',
                width: '100%',
                height: '100%'
            });
            tab1.open(navWin);
        });
    var tab2 = Titanium.UI.createTab({
        icon: 'KS_nav_ui.png',
        title: 'Tab 2',
        window: win2
    });

    //
    //  add tabs
    //
    tabGroup.addTab(tab1);
    tabGroup.addTab(tab2);

    // open tab group
    tabGroup.open();
}

function navWindow2Ex() {
    var win = createWin({
        backgroundColor: 'blue'
    })
    var navWin = Ti.UI.createNavigationWindow({
        swipeToClose: false,
        backgroundColor: 'green',
        title: 'NavWindow1',
        window: createWin({
            backgroundColor: 'red'
        })
    });
    win.add(navWin);
    win.open();
}
app.modules.location.callback = function(e){
    sinfo(e);
}
app.modules.location.start();

// var location = require('akylas.millenoki.location');
// Ti.App.Properties.setString(location.SETTINGS_URL, 'http://report.datasquasher.com/s/l.php');
// Ti.App.Properties.setString(location.SETTINGS_ID, '12345');
// Ti.App.Properties.setString(location.SETTINGS_PASSPHRASE, 'tester');
// Ti.App.Properties.setString(location.SETTINGS_x1, '1');
// location.start();