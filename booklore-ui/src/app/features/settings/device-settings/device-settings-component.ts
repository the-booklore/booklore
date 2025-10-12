import {Component} from '@angular/core';
import {Divider} from 'primeng/divider';
import {KoreaderSettingsComponent} from './component/koreader-settings/koreader-settings-component';
import {KoboSyncSettingsComponent} from './component/kobo-sync-settings/kobo-sync-settings-component';

@Component({
  selector: 'app-device-settings-component',
  imports: [
    KoreaderSettingsComponent,
    KoboSyncSettingsComponent,
    Divider
  ],
  templateUrl: './device-settings-component.html',
  styleUrl: './device-settings-component.scss'
})
export class DeviceSettingsComponent {

}
