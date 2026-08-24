(function () {
  "use strict";

  // ============================================================
  // DEEP SHIELD PRIVACY PROBE v2
  // ============================================================

  const PROBE_VERSION = "11.0.1";
  const NATIVE_APP = "deepshield";

  console.error(
    "[DEEPSHIELD_PROBE] START " +
    location.href
  );


  // ============================================================
  // RESULTAT STRUCTURE
  // ============================================================

  const result = {

    meta: {
      version: PROBE_VERSION,
      url: location.href,
      origin: location.origin,
      startedAt: new Date().toISOString()
    },

    navigator: {},

    timezone: {},

    screen: {
      cssResolution: {}
    },

    networkInformation: {},

    audio: {},

    webgl: {},

    canvas: {},

    fonts: {
      testedCandidates: []
    },

    mediaDevices: {},

    webrtc: {},

    errors: []
  };


  // ============================================================
  // LOGCAT
  // ============================================================

  function log(name, value) {

    try {

      console.error(
        "[DEEPSHIELD_PROBE] " +
        name +
        " = " +
        String(value)
      );

    } catch (_) {}
  }


  function recordError(
    section,
    error
  ) {

    const message =
      String(error);

    result.errors.push({
      section: section,
      error: message
    });

    log(
      section,
      "ERROR:" +
      message
    );
  }


  // ============================================================
  // SHA256
  // ============================================================

  async function sha256(value) {

    try {

      const bytes =
        new TextEncoder()
          .encode(value);


      const hash =
        await crypto.subtle.digest(
          "SHA-256",
          bytes
        );


      return Array
        .from(
          new Uint8Array(hash)
        )
        .map(
          function (b) {

            return b
              .toString(16)
              .padStart(2, "0");
          }
        )
        .join("");

    } catch (e) {

      return (
        "ERROR:" +
        String(e)
      );
    }
  }


  async function sendNativePayload(
    payload
  ) {

    const message = {
      type: "privacy_audit",
      payload: payload
    };


    try {

      if (
        typeof browser !== "undefined" &&
        browser.runtime &&
        typeof browser.runtime.sendNativeMessage === "function"
      ) {

        await browser.runtime
          .sendNativeMessage(
            "deepshield",
            message
          );


        log(
          "nativeMessage",
          "sent"
        );


        return;
      }

    } catch (e) {

      log(
        "nativeMessage.browser",
        "ERROR:" +
        String(e)
      );
    }


    try {

      if (
        typeof chrome !== "undefined" &&
        chrome.runtime &&
        typeof chrome.runtime.sendNativeMessage === "function"
      ) {

        await new Promise(
          function (resolve) {

            chrome.runtime
              .sendNativeMessage(
                "deepshield",
                message,
                function () {

                  try {

                    if (
                      chrome.runtime.lastError
                    ) {

                      log(
                        "nativeMessage.chrome",
                        "ERROR:" +
                        chrome.runtime
                          .lastError
                          .message
                      );

                    } else {

                      log(
                        "nativeMessage",
                        "sent"
                      );
                    }

                  } catch (_) {}


                  resolve();
                }
              );
          }
        );


        return;
      }

    } catch (e) {

      log(
        "nativeMessage.chrome",
        "ERROR:" +
        String(e)
      );
    }


    log(
      "nativeMessage",
      "unavailable"
    );
  }

  // ============================================================
  // RUN
  // ============================================================

  async function run() {

    log(
      "probe.version",
      PROBE_VERSION
    );


    log(
      "URL",
      location.href
    );


    // ==========================================================
    // NAVIGATOR
    // ==========================================================

    try {

      result.navigator.userAgent =
        navigator.userAgent ||
        "";


      result.navigator.platform =
        navigator.platform ||
        "";


      result.navigator.language =
        navigator.language ||
        "";


      result.navigator.languages =
        navigator.languages
          ? Array.from(
              navigator.languages
            )
          : [];


      result.navigator.hardwareConcurrency =
        typeof navigator
          .hardwareConcurrency ===
          "number"
          ? navigator.hardwareConcurrency
          : null;


      result.navigator.maxTouchPoints =
        typeof navigator
          .maxTouchPoints ===
          "number"
          ? navigator.maxTouchPoints
          : null;


      result.navigator.globalPrivacyControl =
        typeof navigator
          .globalPrivacyControl ===
          "boolean"
          ? navigator.globalPrivacyControl
          : null;


      log(
        "userAgent",
        result.navigator.userAgent
      );


      log(
        "platform",
        result.navigator.platform
      );


      log(
        "language",
        result.navigator.language
      );


      log(
        "languages",
        result.navigator.languages.length
          ? result.navigator.languages.join(",")
          : "not exposed"
      );


      log(
        "hardwareConcurrency",
        result.navigator.hardwareConcurrency
      );


      log(
        "maxTouchPoints",
        result.navigator.maxTouchPoints
      );


      log(
        "globalPrivacyControl",
        result.navigator.globalPrivacyControl
      );

    } catch (e) {

      recordError(
        "navigator",
        e
      );
    }


    // ==========================================================
    // TIMEZONE
    // ==========================================================

    try {

      result.timezone.name =
        Intl
          .DateTimeFormat()
          .resolvedOptions()
          .timeZone ||
        null;


      result.timezone.offsetMinutes =
        new Date()
          .getTimezoneOffset();


      log(
        "timezone",
        result.timezone.name
      );


      log(
        "timezoneOffset",
        result.timezone.offsetMinutes
      );

    } catch (e) {

      recordError(
        "timezone",
        e
      );
    }


    // ==========================================================
    // SCREEN
    // ==========================================================

    try {

      result.screen.width =
        screen.width;


      result.screen.height =
        screen.height;


      result.screen.availWidth =
        screen.availWidth;


      result.screen.availHeight =
        screen.availHeight;


      result.screen.colorDepth =
        screen.colorDepth;


      result.screen.pixelDepth =
        screen.pixelDepth;


      result.screen.devicePixelRatio =
        window.devicePixelRatio;


      log(
        "screen",
        result.screen.width +
        "x" +
        result.screen.height
      );


      log(
        "availScreen",
        result.screen.availWidth +
        "x" +
        result.screen.availHeight
      );


      log(
        "colorDepth",
        result.screen.colorDepth
      );


      log(
        "devicePixelRatio",
        result.screen.devicePixelRatio
      );

    } catch (e) {

      recordError(
        "screen",
        e
      );
    }


    // ==========================================================
    // CSS RESOLUTION / DPR
    // ==========================================================

    try {

      result.screen
        .cssResolution
        .dppx1 =
        matchMedia(
          "(resolution: 1dppx)"
        ).matches;


      result.screen
        .cssResolution
        .dppx2 =
        matchMedia(
          "(resolution: 2dppx)"
        ).matches;


      result.screen
        .cssResolution
        .dppx3 =
        matchMedia(
          "(resolution: 3dppx)"
        ).matches;


      result.screen
        .cssResolution
        .min2dppx =
        matchMedia(
          "(min-resolution: 2dppx)"
        ).matches;


      result.screen
        .cssResolution
        .min3dppx =
        matchMedia(
          "(min-resolution: 3dppx)"
        ).matches;


      log(
        "cssResolution.1dppx",
        result.screen
          .cssResolution
          .dppx1
      );


      log(
        "cssResolution.2dppx",
        result.screen
          .cssResolution
          .dppx2
      );


      log(
        "cssResolution.3dppx",
        result.screen
          .cssResolution
          .dppx3
      );


      log(
        "cssResolution.min2dppx",
        result.screen
          .cssResolution
          .min2dppx
      );


      log(
        "cssResolution.min3dppx",
        result.screen
          .cssResolution
          .min3dppx
      );

    } catch (e) {

      recordError(
        "cssResolution",
        e
      );
    }


    // ==========================================================
    // NETWORK INFORMATION
    // ==========================================================

    try {

      const connection =
        navigator.connection ||
        navigator.mozConnection ||
        navigator.webkitConnection ||
        null;


      result.networkInformation.exposed =
        !!connection;


      if (connection) {

        result.networkInformation.effectiveType =
          connection.effectiveType ||
          null;


        result.networkInformation.downlink =
          typeof connection.downlink ===
            "number"
            ? connection.downlink
            : null;


        result.networkInformation.rtt =
          typeof connection.rtt ===
            "number"
            ? connection.rtt
            : null;


        result.networkInformation.saveData =
          typeof connection.saveData ===
            "boolean"
            ? connection.saveData
            : null;


        log(
          "connection.effectiveType",
          result.networkInformation.effectiveType
        );


        log(
          "connection.downlink",
          result.networkInformation.downlink
        );


        log(
          "connection.rtt",
          result.networkInformation.rtt
        );


        log(
          "connection.saveData",
          result.networkInformation.saveData
        );

      } else {

        log(
          "connection",
          "not exposed"
        );
      }

    } catch (e) {

      recordError(
        "networkInformation",
        e
      );
    }


    // ==========================================================
    // AUDIO CONTEXT
    // ==========================================================

    try {

      const AudioCtx =
        window.AudioContext ||
        window.webkitAudioContext;


      result.audio.available =
        !!AudioCtx;


      if (!AudioCtx) {

        log(
          "audio",
          "AudioContext unavailable"
        );

      } else {

        const audio =
          new AudioCtx();


        result.audio.sampleRate =
          audio.sampleRate;


        result.audio.baseLatency =
          typeof audio.baseLatency ===
            "number"
            ? audio.baseLatency
            : null;


        result.audio.outputLatency =
          typeof audio.outputLatency ===
            "number"
            ? audio.outputLatency
            : null;


        log(
          "audio.sampleRate",
          result.audio.sampleRate
        );


        log(
          "audio.baseLatency",
          result.audio.baseLatency === null
            ? "not exposed"
            : result.audio.baseLatency
        );


        log(
          "audio.outputLatency",
          result.audio.outputLatency === null
            ? "not exposed"
            : result.audio.outputLatency
        );


        try {

          await audio.close();

        } catch (_) {}
      }

    } catch (e) {

      recordError(
        "audio",
        e
      );
    }


    // ==========================================================
    // WEBGL
    // ==========================================================

    try {

      const canvas =
        document.createElement(
          "canvas"
        );


      const gl =
        canvas.getContext(
          "webgl"
        ) ||
        canvas.getContext(
          "experimental-webgl"
        );


      result.webgl.available =
        !!gl;


      if (!gl) {

        log(
          "webgl",
          "unavailable"
        );

      } else {

        result.webgl.vendor =
          gl.getParameter(
            gl.VENDOR
          );


        result.webgl.renderer =
          gl.getParameter(
            gl.RENDERER
          );


        result.webgl.version =
          gl.getParameter(
            gl.VERSION
          );


        result.webgl.shadingLanguageVersion =
          gl.getParameter(
            gl.SHADING_LANGUAGE_VERSION
          );


        const debugInfo =
          gl.getExtension(
            "WEBGL_debug_renderer_info"
          );


        result.webgl
          .debugRendererExtensionExposed =
          !!debugInfo;


        if (debugInfo) {

          result.webgl.unmaskedVendor =
            gl.getParameter(
              debugInfo
                .UNMASKED_VENDOR_WEBGL
            );


          result.webgl.unmaskedRenderer =
            gl.getParameter(
              debugInfo
                .UNMASKED_RENDERER_WEBGL
            );

        } else {

          result.webgl.unmaskedVendor =
            null;


          result.webgl.unmaskedRenderer =
            null;
        }


        log(
          "webgl.vendor",
          result.webgl.vendor
        );


        log(
          "webgl.renderer",
          result.webgl.renderer
        );


        log(
          "webgl.unmaskedVendor",
          result.webgl.unmaskedVendor ===
            null
            ? "not exposed"
            : result.webgl.unmaskedVendor
        );


        log(
          "webgl.unmaskedRenderer",
          result.webgl.unmaskedRenderer ===
            null
            ? "not exposed"
            : result.webgl.unmaskedRenderer
        );
      }

    } catch (e) {

      recordError(
        "webgl",
        e
      );
    }


    // ==========================================================
    // CANVAS
    // ==========================================================

    try {

      const canvas =
        document.createElement(
          "canvas"
        );


      canvas.width =
        320;


      canvas.height =
        80;


      const ctx =
        canvas.getContext(
          "2d"
        );


      result.canvas.available =
        !!ctx;


      if (!ctx) {

        log(
          "canvas",
          "2D context unavailable"
        );

      } else {

        ctx.fillStyle =
          "#f60";


        ctx.fillRect(
          20,
          10,
          120,
          40
        );


        ctx.fillStyle =
          "#069";


        ctx.font =
          "18px Arial";


        ctx.textBaseline =
          "top";


        ctx.fillText(
          "Deliriuum Deep Shield",
          10,
          20
        );


        /*
         * Trois lectures du même Canvas.
         *
         * IMPORTANT :
         * stable=true n'est PAS un verdict.
         * Kotlin interprétera ensuite le comportement.
         */
        const hashes =
          [];


        for (
          let i = 0;
          i < 3;
          i++
        ) {

          const dataURL =
            canvas.toDataURL(
              "image/png"
            );


          hashes.push(
            await sha256(
              dataURL
            )
          );
        }


        result.canvas.hashes =
          hashes;


        result.canvas.uniqueHashCount =
          new Set(
            hashes
          ).size;


        result.canvas.stable =
          result.canvas.uniqueHashCount ===
          1;


        log(
          "canvas.sha256",
          hashes[0]
        );


        log(
          "canvas.hashes",
          hashes.join(" | ")
        );


        log(
          "canvas.uniqueHashCount",
          result.canvas.uniqueHashCount
        );


        log(
          "canvas.stable",
          result.canvas.stable
        );
      }

    } catch (e) {

      recordError(
        "canvas",
        e
      );
    }


    // ==========================================================
    // FONTS
    // ==========================================================

    try {

      const fontCandidates = [
        "Arial",
        "Arial Black",
        "Verdana",
        "Tahoma",
        "Trebuchet MS",
        "Times New Roman",
        "Georgia",
        "Courier New",
        "Comic Sans MS",
        "Impact",
        "Roboto",
        "Noto Sans",
        "Noto Serif",
        "Helvetica",
        "Helvetica Neue",
        "Samsung Sans",
        "OnePlus Sans",
        "MiSans",
        "Ubuntu",
        "DejaVu Sans",
        "Liberation Sans"
      ];


      result.fonts.testedCandidates =
        fontCandidates.slice();


      const testText =
        "mmmmmmmmmmlliWW@@##0123456789";


      // --------------------------------------------------------
      // Canvas
      // --------------------------------------------------------

      const fontCanvas =
        document.createElement(
          "canvas"
        );


      const fontCtx =
        fontCanvas.getContext(
          "2d"
        );


      const canvasDetected =
        [];


      if (fontCtx) {

        const baseFamilies = [
          "monospace",
          "sans-serif",
          "serif"
        ];


        const baseline =
          Object.create(
            null
          );


        for (
          const base of baseFamilies
        ) {

          fontCtx.font =
            "72px " +
            base;


          baseline[base] =
            fontCtx.measureText(
              testText
            ).width;
        }


        for (
          const font of fontCandidates
        ) {

          let found =
            false;


          for (
            const base of baseFamilies
          ) {

            fontCtx.font =
              '72px "' +
              font +
              '",' +
              base;


            const width =
              fontCtx.measureText(
                testText
              ).width;


            if (
              Math.abs(
                width -
                baseline[base]
              ) >
              0.01
            ) {

              found =
                true;

              break;
            }
          }


          if (found) {

            canvasDetected.push(
              font
            );
          }
        }
      }


      result.fonts.canvasDetected =
        canvasDetected;


      result.fonts.canvasCount =
        canvasDetected.length;


      log(
        "fonts.canvas.count",
        result.fonts.canvasCount
      );


      log(
        "fonts.canvas.detected",
        canvasDetected.length
          ? canvasDetected.join(", ")
          : "none"
      );


      // --------------------------------------------------------
      // DOM
      // --------------------------------------------------------

      function measureDOMFont(
        family
      ) {

        const span =
          document.createElement(
            "span"
          );


        span.textContent =
          testText;


        span.style.cssText =
          [
            "position:absolute",
            "left:-99999px",
            "top:-99999px",
            "visibility:hidden",
            "white-space:nowrap",
            "font-size:72px",
            "font-family:" +
              family
          ].join(";");


        (
          document.body ||
          document.documentElement
        ).appendChild(
          span
        );


        const width =
          span
            .getBoundingClientRect()
            .width;


        span.remove();


        return width;
      }


      const domBaseline =
        measureDOMFont(
          "monospace"
        );


      const domDetected =
        [];


      for (
        const font of fontCandidates
      ) {

        try {

          const width =
            measureDOMFont(
              '"' +
              font +
              '",monospace'
            );


          if (
            Math.abs(
              width -
              domBaseline
            ) >
            0.01
          ) {

            domDetected.push(
              font
            );
          }

        } catch (_) {}
      }


      result.fonts.domDetected =
        domDetected;


      result.fonts.domCount =
        domDetected.length;


      log(
        "fonts.dom.count",
        result.fonts.domCount
      );


      log(
        "fonts.dom.detected",
        domDetected.length
          ? domDetected.join(", ")
          : "none"
      );

    } catch (e) {

      recordError(
        "fonts",
        e
      );
    }


    // ==========================================================
    // MEDIA DEVICES
    // ==========================================================

    try {

      const supported =
        !!(
          navigator.mediaDevices &&
          typeof navigator
            .mediaDevices
            .enumerateDevices ===
            "function"
        );


      result.mediaDevices.supported =
        supported;


      if (supported) {

        const devices =
          await navigator
            .mediaDevices
            .enumerateDevices();


        const summary =
          devices.map(
            function (device) {

              return {
                kind:
                  device.kind ||
                  "",

                labelExposed:
                  !!device.label,

                deviceIdExposed:
                  !!device.deviceId,

                groupIdExposed:
                  !!device.groupId
              };
            }
          );


        result.mediaDevices.count =
          devices.length;


        result.mediaDevices.devices =
          summary;


        result.mediaDevices.labelsExposed =
          summary.some(
            function (device) {

              return device.labelExposed;
            }
          );


        result.mediaDevices.deviceIdsExposed =
          summary.some(
            function (device) {

              return device.deviceIdExposed;
            }
          );


        result.mediaDevices.groupIdsExposed =
          summary.some(
            function (device) {

              return device.groupIdExposed;
            }
          );


        log(
          "mediaDevices.count",
          result.mediaDevices.count
        );


        log(
          "mediaDevices.labelsExposed",
          result.mediaDevices.labelsExposed
        );


        log(
          "mediaDevices.deviceIdsExposed",
          result.mediaDevices.deviceIdsExposed
        );


        log(
          "mediaDevices.groupIdsExposed",
          result.mediaDevices.groupIdsExposed
        );


        /*
         * Format historique conservé.
         */
        log(
          "mediaDevices.details",
          JSON.stringify(
            summary.map(
              function (device) {

                return {
                  kind:
                    device.kind,

                  label:
                    device.labelExposed
                      ? "EXPOSED"
                      : "hidden",

                  deviceId:
                    device.deviceIdExposed
                      ? "present"
                      : "empty",

                  groupId:
                    device.groupIdExposed
                      ? "present"
                      : "empty"
                };
              }
            )
          )
        );

      } else {

        result.mediaDevices.count =
          null;


        log(
          "mediaDevices",
          "not exposed"
        );
      }

    } catch (e) {

      recordError(
        "mediaDevices",
        e
      );
    }


    // ==========================================================
    // WEBRTC
    // ==========================================================

    try {

      const available =
        typeof RTCPeerConnection !==
        "undefined";


      result.webrtc.available =
        available;


      if (!available) {

        result.webrtc.candidateCount =
          0;


        result.webrtc.candidates =
          [];


        result.webrtc.hasHostCandidate =
          false;


        result.webrtc.hasSrflxCandidate =
          false;


        result.webrtc.hasRelayCandidate =
          false;


        log(
          "webrtc",
          "RTCPeerConnection unavailable"
        );

      } else {

        const pc =
          new RTCPeerConnection({
            iceServers: []
          });


        const candidates =
          [];


        pc.createDataChannel(
          "deepshield-probe"
        );


        pc.onicecandidate =
          function (event) {

            if (
              event &&
              event.candidate &&
              event.candidate.candidate
            ) {

              candidates.push(
                event.candidate.candidate
              );
            }
          };


        const offer =
          await pc.createOffer();


        await pc.setLocalDescription(
          offer
        );


        await new Promise(
          function (resolve) {

            setTimeout(
              resolve,
              2000
            );
          }
        );


        try {

          pc.close();

        } catch (_) {}


        result.webrtc.candidateCount =
          candidates.length;


        result.webrtc.candidates =
          candidates;


        result.webrtc.hasHostCandidate =
          candidates.some(
            function (candidate) {

              return /\styp host\s/i
                .test(candidate);
            }
          );


        result.webrtc.hasSrflxCandidate =
          candidates.some(
            function (candidate) {

              return /\styp srflx\s/i
                .test(candidate);
            }
          );


        result.webrtc.hasRelayCandidate =
          candidates.some(
            function (candidate) {

              return /\styp relay\s/i
                .test(candidate);
            }
          );


        log(
          "webrtc.candidates",
          candidates.length
            ? candidates.join(" | ")
            : "none exposed"
        );


        log(
          "webrtc.candidateCount",
          result.webrtc.candidateCount
        );


        log(
          "webrtc.hasHostCandidate",
          result.webrtc.hasHostCandidate
        );


        log(
          "webrtc.hasSrflxCandidate",
          result.webrtc.hasSrflxCandidate
        );


        log(
          "webrtc.hasRelayCandidate",
          result.webrtc.hasRelayCandidate
        );
      }

    } catch (e) {

      recordError(
        "webrtc",
        e
      );
    }


    // ==========================================================
    // FIN
    // ==========================================================

    result.meta.completedAt =
      new Date()
        .toISOString();


    /*
     * Snapshot complet dans Logcat.
     * Très utile pendant le développement du moteur Kotlin.
     */
    try {

      log(
        "RESULT_JSON",
        JSON.stringify(
          result
        )
      );

    } catch (e) {

      recordError(
        "resultSerialization",
        e
      );
    }


    /*
     * Envoi du résultat brut à Android.
     */
    await sendNativePayload(
      result
    );


    log(
      "DONE",
      "OK"
    );
  }


  // ============================================================
  // START
  // ============================================================

  setTimeout(
    function () {

      run()
        .catch(
          function (error) {

            recordError(
              "FATAL",
              error
            );


            /*
             * Même si un test plante, on transmet
             * ce qui a déjà été mesuré.
             */
            sendNativePayload(
              result
            ).catch(
              function (_) {}
            );
          }
        );

    },
    500
  );

})();