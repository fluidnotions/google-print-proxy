//opts tenantKey, jobQueueEndpoint, depBasePath, modalParentSelector, jobQueueDisplaySelector, refreshRate, queueLimit, delTempFile
function GCPClient(opts) {

  var tenantKey = opts.tenantKey || "dummyTenantKey";
  var refreshRate = opts.refreshRate || 5000;
  var queueLimit = opts.queueLimit || 10;
  var hostUrl = opts.hostUrl || "";
  var jobQueueEndpoint = opts.jobQueueEndpoint || "http://localhost:9091/"
  var depBasePath = opts.depBasePath || "assets/gcp/main/";
  var modalParentSelector = opts.modalParentSelector || "#gcp-modal";
  var jobQueueDisplaySelector = opts.jobQueueDisplaySelector || "#jobQueueDisplay";
  var delTempFile = opts.delTempFile || false;
  var refreshJobQueueWorker = true;
  var debugLogging = false;
  var printerList = null;
  var activePrinter = null;

  //testing only
  var addPrintBtn = null; //'.page-content-body';

  //setup util tools
  var tmpls = TemplateUtils("assets/gcp/main/templates/")
  var gcpUtils = {};
  gcpUtils.nofifyResponse = function nofifyResponse(response) {
    if (!response.success) {
      $.notify({
        title: '<strong>Server Error</strong>',
        message: response.message
      }, {
        type: 'danger',
        element: jobQueueDisplaySelector,
        animate: {
          enter: 'animated fadeInDown',
          exit: 'animated fadeOutUp'
        },
        placement: {
          from: "top",
          align: "right"
        }
      });
    }
  }

  gcpUtils.findPrinterObj = function findPrinterObj(printerId) {
    var target = null;
    for (var x = 0; x < printerList.length; x++) {
      if (printerId === printerList[x].id) {
        target = printerList[x];
        break;
      }
    }

    return target;
  }

  gcpUtils.buildRangeIntervalObj = function buildRangeIntervalObj(rangeStr) {
    var range = rangeStr.trim();
    var limObj;
    if (range.indexOf() > -1) {
      var limits = range.split("-");
      limObj = {
        "start": limits[0],
        "end": limits[1]
      }
    } else {
      limObj = {
        "start": range
      }
    }
    return limObj;
  }

  gcpUtils.unicaps = ["page_range",
    "reverse_order",
    "copies",
    "fit_to_page"
  ];

  //not used causes browser to crash
  gcpUtils.cleanOutExtraCDDProps = function cleanOutExtraCDDProps(target) {
    var filPropNames = ["is_default", "name", "custom_display_name"]
    for (var x = 0; filPropNames.length; x++) {
      if (target.hasOwnProperty(filPropNames[x])) {
        delete target[filPropNames[x]];
        if (debugLogging) console.log("removed prop: " + filPropNames[x]);
      }
    }
  };

  gcpUtils.processSelectedCddToCjt = function processSelectedCddToCjt(id) {
    var target = null;
    // we only need to deal with exceptions to the rule of standard index
    // selection on cdd, which
    // are page_range, reverse_order, copies and fit_to_page (supported_content_type not incl on form)
    switch (id) {

      case "supported_content_type":
        //not part of cjt
        break;

      case "page_range":

        if ($('#cloudJobTicketForm #rangeTextInput').length) {
          var pages = $('#cloudJobTicketForm #allTarget').hasClass("active") ? "all" : $('#cloudJobTicketForm #rangeTextInput').val().length === 0 ? "all" : $('#cloudJobTicketForm #rangeTextInput').val();
          if (pages !== 'all') { //if all just ignore
            //eg: 1-5, 8, 11-13
            var intervalArr = [];
            if (pages.indexOf(",") > -1) {
              var ranges = pages.split(",");
              for (var x = 0; x < ranges.length; x++) {
                var range = ranges[x]
                intervalArr.push(gcpUtils.buildRangeIntervalObj(range));
              }
            } else {
              intervalArr.push(gcpUtils.buildRangeIntervalObj(pages));
            }

            target = {
              "interval": intervalArr
            };
          } else {
            target = {
              "interval": [{
                "start": 1
              }]
            };
          }
        }
        break;

      case "reverse_order":

        if ($('#cloudJobTicketForm #reverse_order').length) {
          var reverse = ($('#cloudJobTicketForm #reverse_order').attr('checked') === 'checked');
          target = {
            "reverse_order": reverse
          };
        }
        break;

      case "copies":

        if ($('#cloudJobTicketForm #copies').length) {
          var copies = $('#cloudJobTicketForm #copies').val();
          target = {
            "copies": copies ? copies : 1
          };
        }
        break;

      case "fit_to_page":

        if ($('#cloudJobTicketForm #fit_to_page').length) {
          var fitTo = $('#cloudJobTicketForm #fit_to_page').val();
          target = {
            "type": fitTo ? fitTo : "NO_FITTING"
          };
        }
        break;

      default: //all else are prop otion array index of selection
        var val = $('#cloudJobTicketForm #' + id).val();
        var cap = activePrinter.capabilities.printer[id];
        if (cap && val) {
          target = cap.option[val];
          if (debugLogging) console.log("id: " + id + ", index: " + val + ", target: " + JSON.stringify(target));
          //gcpUtils.cleanOutExtraCDDProps(target);
        }
        break;
    }

    return target
  };

  var usernameForStoredCredentialRetrieval = function usernameForStoredCredentialRetrieval() {
      return {
        username: Lockr.get('identityEmail') || "justin@fluidnotions.com"
      }
    },
    init = function init() {

      if (addPrintBtn) {
        addPrintButtonToPage();
      }

      //works for multiple buttons on a page
      $(".gcp-print").click(function(e) {
        var url = e.currentTarget.dataset['dynamicDocUrl'];
        var title = e.currentTarget.dataset['title'];
        var contentType = e.currentTarget.dataset['mime'];

        var param = $.extend({}, usernameForStoredCredentialRetrieval(), {
          tenantKey: tenantKey,
          url: url,
          contentType: contentType
        });
        if (title) {
          param.title = title;
        }
        if (debugLogging) console.log("gcp-print clicked! downloadtmpdoc")
        ajaxPost(hostUrl + "downloadtmpdoc", param)
          .then(function(data) {
            nofifyResponse(data);
            if (debugLogging) console.log("downloadtmpdoc response: " + JSON.stringify(data));
            return showGcpClientModal({
              title: title,
              contentType: contentType,
              tempFileDirPath: data.dst
            });
          });
      });

      //start web worker to poll for job queue status
      if (refreshJobQueueWorker) {
        //have to render list seperately to the wrapper else refresh closes the dropdown
        tmpls.renderExtTemplate({
          name: 'jobQueueDisplayWrapper',
          selector: jobQueueDisplaySelector,
          data: null
        }).then(function() {

          var worker = new Worker(depBasePath + 'refreshJobQueueWorker.js');
          worker.addEventListener('message', function(e) {

            var dataObject = JSON.parse(e.data);
            if (debugLogging) console.log('Worker said: dataObject.jobs: ', JSON.stringify(dataObject.jobs, null, 2));
            if (dataObject) {
              refreshJobQueueDisplay(prepareJobQueueData(dataObject.jobs));
            }
          }, false);

          var work = $.extend({}, usernameForStoredCredentialRetrieval(), {
            cmd: 'start',
            refreshRate: refreshRate,
            url: jobQueueEndpoint
          });
          //zero or null is falsey
          if (refreshRate) worker.postMessage(work);
          return null;
        });
      }
    },
    addPrintButtonToPage = function addPrintButtonToPage() {
      return tmpls.renderExtTemplate({
        name: 'printButton',
        selector: addPrintBtn,
        data: null
      });
    },
    prepareJobQueueData = function prepareJobQueueData(jobs) {
      var jobQueueDisplayData = {};
      var notDone = 0;
      if (jobs) {
        var len = jobs.length < queueLimit ? jobs.length : queueLimit + 1;
        for (var i = 0; i < len; i++) {
          if (jobs[i].status === "IN_PROGRESS" || jobs[i].status === "QUEUED") {
            notDone++;
          }
        }
      }

      if (notDone > 0) {
        $('#queuedCount').text(notDone);
      } else {
        $('#queuedCount').empty();
      }
      if (jobs && jobs.length > 0) {
        jobs = jobs.slice(0, queueLimit + 1);
      }

      jobQueueDisplayData.jobs = jobs || [];
      return jobQueueDisplayData;
    },
    refreshJobQueueDisplay = function refreshJobQueueDisplay(jobQueueDisplayData) {
      if (debugLogging) console.log("rendering jobQueueDisplayTmpl");
      //have to render list seperately to the wrapper else refresh closes the dropdown
      tmpls.renderExtTemplate({
        name: 'jobQueueDisplay',
        selector: '#jobQueueList',
        data: jobQueueDisplayData.jobs
      });
    },
    cjtpropsToCJT = function cjtpropsToCJP(cjtprops) {
      var cjt = {
        "version": "1.0",
        "print": cjtprops
      }
      return cjt;
    },
    showGcpClientModal = function showGcpClientModal(printTargetParams) {
      //1. get printer list from OT server/mockup local json
      var params = $.extend({}, usernameForStoredCredentialRetrieval(), {
        tenantKey: tenantKey
      });
      return ajaxPost(hostUrl + "printers", params)
        .bind({})
        .then(function(data) {
          nofifyResponse(data);
          if (debugLogging) console.log("printers: " + JSON.stringify(data));
          printerList = this.printerList = data.printers;
          //hack solution to weird problem
          // for (var p = 0; p < printerList.length; p++) {
          //   printerList[p].gson = null;
          // }
          if (debugLogging) console.log("printers: " + JSON.stringify(printerList, null, 2));
          //add modal to page body
          return tmpls.renderExtTemplate({
            name: 'modal',
            selector: modalParentSelector,
            data: printTargetParams
          });
        }).then(function() {
          // 2. add printer items to searchable list
          return loadPrinterSelectForm(this.printerList);
        }).then(function() {
          // 3. show dialog
          $('#gcp').modal({
            height: $(window).height() - 300
          });

          $('.gcp-ui-wrapper').find('#printBtn').click(function(e) {
            //use gcpUtils to get object from active printer cdd for selected index
            //since we changed our input form elements to match the properties of the
            //cdd we can now use cdd property names to find selected option indexes
            //or deal with special cases in gcpUtils.processSelectedCddToCjt

            //add universal controls gcp 2.0 can handle these even if the printer cannot
            for (var i = 0; i < gcpUtils.unicaps.length; i++) {
              var unicap = gcpUtils.unicaps[i];
              if (!activePrinter.capabilities.printer.hasOwnProperty(unicap)) {
                activePrinter.capabilities.printer[unicap] = null;
              }
            }

            var cjtprops = {};
            for (var prop in activePrinter.capabilities.printer) {
              var item = gcpUtils.processSelectedCddToCjt(prop);
              //skip any nulls
              if (item) {
                cjtprops[prop] = item;
              }
            }
            if (debugLogging) console.log("form input (to cjtprops): " + JSON.stringify(cjtprops, null, 2));
            submitPrintJob(cjtpropsToCJT(cjtprops), printTargetParams);
          });
        }).bind();
    },
    submitPrintJob = function submitPrintJob(cjt, printTargetParams) {
      var job = $.extend({}, usernameForStoredCredentialRetrieval(), {
        ticket: JSON.stringify(cjt),
        title: printTargetParams.title,
        contentType: printTargetParams.contentType,
        printerId: activePrinter.id,
        tempFileDirPath: printTargetParams.tempFileDirPath,
        tenantKey: tenantKey
      });
      if (debugLogging) console.log("about to post job: " + JSON.stringify(job, null, 2));
      return ajaxPost(hostUrl + "print", job)
        .then(function(data) {
          nofifyResponse(data);
          if (debugLogging) console.log("print job submit response: status: " + (data.success ? "success" : "fail") + ", message: " + data.message);
          if (delTempFile) {
            $.ajaxPost(hostUrl + "deltemp", {
              tempFileDirPath: printTargetParams.tempFileDirPath
            });
          }
          $('#gcp').modal('hide');
        });
    },
    loadPrinterSelectForm = function loadPrinterSelectForm(printerList) {
      if (debugLogging) console.log("rendering printerSelectFormTmpl");
      return tmpls.renderExtTemplate({
        name: 'printerSelectForm',
        selector: $('.gcp-ui-wrapper .modal-body'),
        data: null
      }).then(function() {
        if (debugLogging) console.log("rendering printerItemTmpl");
        return tmpls.renderExtTemplate({
          name: 'printerItem',
          selector: '#printerSearchlist',
          data: printerList
        });
      }).then(function() {
        $('.gcp-ui-wrapper .modal-body').find('#printerSearchlist').btsListFilter('#searchinput', {
          itemEl: '.listFilter-item',
          itemChild: '.listFilter-item-target'
        });
        return null;
      }).then(function() {
        $('.listFilter-item').click(function(e) {
          var id = e.currentTarget.id;
          if (debugLogging) console.log("id: " + id || "is null");
          var printerId = id.split("=")[1];
          if (debugLogging) console.log("printer id: " + printerId);
          loadPrintJobSubmitForm(printerId);
        });
        return null;
      });
    },
    loadPrintJobSubmitForm = function loadPrintJobSubmitForm(printerId) {
      activePrinter = null;
      var params = $.extend({}, usernameForStoredCredentialRetrieval(), {
        tenantKey: tenantKey,
        printerId: printerId
      });
      ajaxPost(hostUrl + "printer", params)
        .then(function(data) {
          nofifyResponse(data);
          var capabilities = data.printers[0].capabilities;
          if (debugLogging) console.log("##printer capabilities: " + JSON.stringify(capabilities, null, 2));
          activePrinter = gcpUtils.findPrinterObj(printerId);
          if (printerId !== "__google__docs") {
            activePrinter.isPrinter = true;
          }
          activePrinter.capabilities = capabilities;
          if (debugLogging) console.log("rendering printJobSubmitTmpl");
          return tmpls.renderExtTemplate({
            name: 'printJobSubmit',
            selector: $('.gcp-ui-wrapper .modal-body'),
            data: activePrinter
          }).then(function() {
            $('.gcp-ui-wrapper .modal-body').find('.selectpicker').selectpicker();
            $('.gcp-ui-wrapper .modal-body').find('#back').click(function(e) {
              loadPrinterSelectForm(printerList);
            });
            $('.gcp-ui-wrapper .modal-body').find('#rangeTarget').click(function(e) {
              $('.gcp-ui-wrapper .modal-body').find('#rangeTextInput').css("display", "initial");
            });
            $('.gcp-ui-wrapper .modal-body').find('#allTarget').click(function(e) {
              $('.gcp-ui-wrapper .modal-body').find('#rangeTextInput').css("display", "none");
            });
            $('.gcp-ui-wrapper').find('#printBtn').removeClass("disabled");
          });
        });
    }

  return {
    init: init
  }

}
