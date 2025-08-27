import {Component} from '@angular/core';
import {KoreaderSettingsComponent} from './koreader-settings-component/koreader-settings-component';
import {KoboSyncSettingsComponent} from './kobo-sync-settings-component/kobo-sync-settings-component';
import {Divider} from 'primeng/divider';

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
