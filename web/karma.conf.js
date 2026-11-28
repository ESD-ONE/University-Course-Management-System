// Karma configuration: Angular CLI defaults (`ng generate config karma`), plus what CI
// needs - a headless Chrome that runs inside a container, an lcov report for SonarQube
// (sonar-project.properties reads coverage/web/lcov.info) and a JUnit report for Jenkins.
module.exports = function (config) {
  config.set({
    basePath: '',
    frameworks: ['jasmine', '@angular-devkit/build-angular'],
    plugins: [
      require('karma-jasmine'),
      require('karma-chrome-launcher'),
      require('karma-jasmine-html-reporter'),
      require('karma-coverage'),
      require('karma-junit-reporter'),
      require('@angular-devkit/build-angular/plugins/karma'),
    ],
    client: {
      jasmine: {},
      clearContext: false,
    },
    jasmineHtmlReporter: {
      suppressAll: true,
    },
    coverageReporter: {
      dir: require('path').join(__dirname, './coverage/web'),
      subdir: '.',
      reporters: [{ type: 'html' }, { type: 'text-summary' }, { type: 'lcovonly' }],
    },
    // Used by `npm run test:ci`: Jenkins shows these results next to the backend's.
    junitReporter: {
      outputDir: require('path').join(__dirname, './test-results'),
      outputFile: 'karma.xml',
      useBrowserName: false,
    },
    reporters: ['progress', 'kjhtml'],
    browsers: ['Chrome'],
    customLaunchers: {
      ChromeHeadlessCI: {
        base: 'ChromeHeadless',
        flags: [
          // Chrome's sandbox cannot start inside a container running as root.
          '--no-sandbox',
          // A container gets 64 MB of /dev/shm, which Chrome fills with a test bundle
          // this size; the page then stalls. This makes it use /tmp instead.
          '--disable-dev-shm-usage',
          '--disable-gpu',
        ],
      },
    },
    // The build host is slow: loading the instrumented bundle can take longer than
    // Karma's default 30 seconds, after which it gave up before running a single test.
    captureTimeout: 180000,
    browserNoActivityTimeout: 180000,
    browserDisconnectTimeout: 30000,
    browserDisconnectTolerance: 1,
    restartOnFileChange: true,
  });
};
