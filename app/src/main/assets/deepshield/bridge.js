(function () {
  "use strict";

  const MESSAGE_SOURCE =
    "DELIRIIUM_PRIVACY_AUDIT_MAIN";

  const EXPECTED_ORIGIN =
    "https://example.com";


  // ============================================================
  // EVITER DOUBLE INSTALLATION
  // ============================================================

  if (
    window.__DELIRIIUM_PRIVACY_BRIDGE_INSTALLED__
  ) {
    return;
  }

  window.__DELIRIIUM_PRIVACY_BRIDGE_INSTALLED__ =
    true;


  // ============================================================
  // MAIN -> ISOLATED -> ANDROID
  // ============================================================

  window.addEventListener(
    "message",
    function (event) {

      try {

        if (
          window.location.origin !==
          EXPECTED_ORIGIN
        ) {
          return;
        }

        if (
          event.origin !==
          EXPECTED_ORIGIN
        ) {
          return;
        }


        const data =
          event.data;


        if (
          !data ||
          data.source !==
          MESSAGE_SOURCE ||
          data.type !==
          "privacy_audit" ||
          !data.payload ||
          typeof data.payload !==
          "object"
        ) {
          return;
        }


        console.error(
          "[DEEPSHIELD_BRIDGE] payload received from MAIN"
        );


        browser.runtime
          .sendNativeMessage(
            "deepshield",
            {
              type:
                "privacy_audit",

              payload:
                data.payload
            }
          )
          .then(
            function () {

              console.error(
                "[DEEPSHIELD_BRIDGE] native message sent"
              );
            }
          )
          .catch(
            function (error) {

              console.error(
                "[DEEPSHIELD_BRIDGE] native message error: " +
                String(error)
              );
            }
          );

      } catch (error) {

        console.error(
          "[DEEPSHIELD_BRIDGE] error: " +
          String(error)
        );
      }
    }
  );


  // ============================================================
  // INJECTION DU PROBE DANS LE VRAI MONDE DE LA PAGE
  // ============================================================

  function injectProbe() {

    const root =
      document.documentElement ||
      document.head ||
      document.body;


    if (!root) {

      setTimeout(
        injectProbe,
        0
      );

      return;
    }


    try {

      const script =
        document.createElement(
          "script"
        );


      script.src =
        browser.runtime.getURL(
          "probe.js"
        );


      script.async =
        false;


      script.onload =
        function () {

          console.error(
            "[DEEPSHIELD_BRIDGE] probe injected in MAIN"
          );

          try {
            script.remove();
          } catch (_) {}
        };


      script.onerror =
        function (error) {

          console.error(
            "[DEEPSHIELD_BRIDGE] probe injection failed: " +
            String(error)
          );
        };


      root.appendChild(
        script
      );

    } catch (error) {

      console.error(
        "[DEEPSHIELD_BRIDGE] injection error: " +
        String(error)
      );
    }
  }


  injectProbe();

})();