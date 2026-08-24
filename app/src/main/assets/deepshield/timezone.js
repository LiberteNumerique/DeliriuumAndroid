(function () {
  "use strict";

  const TARGET_TIMEZONE =
    "Atlantic/Reykjavik";

  const TARGET_OFFSET_MINUTES =
    0;


  // ============================================================
  // Intl.DateTimeFormat
  // ============================================================

  try {

    const OriginalDateTimeFormat =
      Intl.DateTimeFormat;


    const OriginalResolvedOptions =
      OriginalDateTimeFormat
        .prototype
        .resolvedOptions;


    OriginalDateTimeFormat
      .prototype
      .resolvedOptions =
      function () {

        const result =
          OriginalResolvedOptions
            .call(this);


        try {

          result.timeZone =
            TARGET_TIMEZONE;

        } catch (_) {}


        return result;
      };

  } catch (_) {}


  // ============================================================
  // Date.getTimezoneOffset
  // ============================================================

  try {

    Object.defineProperty(
      Date.prototype,
      "getTimezoneOffset",
      {
        configurable: true,
        writable: true,
        value: function () {
          return TARGET_OFFSET_MINUTES;
        }
      }
    );

  } catch (_) {}


  // ============================================================
  // Date formatting without explicit timezone
  // ============================================================

  try {

    const originalToLocaleString =
      Date.prototype
        .toLocaleString;


    Date.prototype
      .toLocaleString =
      function (
        locales,
        options
      ) {

        const safeOptions =
          Object.assign(
            {},
            options || {}
          );


        if (
          !safeOptions.timeZone
        ) {

          safeOptions.timeZone =
            TARGET_TIMEZONE;
        }


        return originalToLocaleString
          .call(
            this,
            locales,
            safeOptions
          );
      };

  } catch (_) {}


  try {

    const originalToLocaleDateString =
      Date.prototype
        .toLocaleDateString;


    Date.prototype
      .toLocaleDateString =
      function (
        locales,
        options
      ) {

        const safeOptions =
          Object.assign(
            {},
            options || {}
          );


        if (
          !safeOptions.timeZone
        ) {

          safeOptions.timeZone =
            TARGET_TIMEZONE;
        }


        return originalToLocaleDateString
          .call(
            this,
            locales,
            safeOptions
          );
      };

  } catch (_) {}


  try {

    const originalToLocaleTimeString =
      Date.prototype
        .toLocaleTimeString;


    Date.prototype
      .toLocaleTimeString =
      function (
        locales,
        options
      ) {

        const safeOptions =
          Object.assign(
            {},
            options || {}
          );


        if (
          !safeOptions.timeZone
        ) {

          safeOptions.timeZone =
            TARGET_TIMEZONE;
        }


        return originalToLocaleTimeString
          .call(
            this,
            locales,
            safeOptions
          );
      };

  } catch (_) {}

})();