import {Component, Input, OnInit} from "@angular/core";

@Component({
  selector: 'app-icon-name-component',
  imports: [],
  templateUrl: './icon-name-component.html',
  styleUrl: './icon-name-component.scss'
})

export class IconNameComponent implements OnInit {
  @Input() icon: null | undefined | string = '';

  iconName: string | null = null;

  ngOnInit(): void {
    if (!this.icon) {
      return;
    }

    if (this.icon?.endsWith('.svg')) {
      this.iconName = this.icon
    } else {
      this.iconName = this.icon.startsWith('pi') ? this.icon : `pi pi-${this.icon}`;
    }
  }
}
