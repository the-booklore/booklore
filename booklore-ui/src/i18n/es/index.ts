import {Translation} from '@jsverse/transloco';
import common from './common.json';
import auth from './auth.json';
import nav from './nav.json';
import dashboard from './dashboard.json';
import settings from './settings.json';
import settingsEmail from './settings-email.json';
import settingsReader from './settings-reader.json';
import settingsView from './settings-view.json';
import settingsMeta from './settings-metadata.json';
import settingsLibMeta from './settings-library-metadata.json';
import settingsApp from './settings-application.json';
import settingsUsers from './settings-users.json';
import settingsNaming from './settings-naming.json';
import settingsOpds from './settings-opds.json';
import settingsTasks from './settings-tasks.json';
import settingsAuth from './settings-auth.json';
import settingsDevice from './settings-device.json';

// Settings tabs each get their own file: settings-email, settings-reader, settings-view, etc.
const translations: Translation = {common, auth, nav, dashboard, settings, settingsEmail, settingsReader, settingsView, settingsMeta, settingsLibMeta, settingsApp, settingsUsers, settingsNaming, settingsOpds, settingsTasks, settingsAuth, settingsDevice};
export default translations;
