import 'zone.js';
import 'zone.js/testing';
import 'zone.js/plugins/zone-patch-rxjs-fake-async';
import {TestBed} from '@angular/core/testing';
import {BrowserDynamicTestingModule, platformBrowserDynamicTesting} from '@angular/platform-browser-dynamic/testing';

// Only initialize if not already initialized
if (!(globalThis as any).__ANGULAR_TESTBED_INITIALIZED__) {
  (globalThis as any).__ANGULAR_TESTBED_INITIALIZED__ = true;
  TestBed.initTestEnvironment(
    BrowserDynamicTestingModule,
    platformBrowserDynamicTesting(),
    {teardown: {destroyAfterEach: true}}
  );
}
