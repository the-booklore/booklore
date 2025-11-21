import {Component, ElementRef, Input, OnInit, ViewChild} from "@angular/core";

@Component({
  selector: 'app-icon-component',
  imports: [],
  templateUrl: './icon-component.html',
  styleUrl: './icon-component.scss'
})

export class IconComponent implements OnInit {
  @Input() icon: null | undefined | string = '';
  @ViewChild('svgWrapper', {static: false}) svgWrapper!: ElementRef;

  isExternalSVG = false;
  iconClassName = '';

  ngOnInit(): void {
    if (!this.icon) {
      return;
    }

    this.isExternalSVG = !!this.icon?.endsWith('.svg');

    if (this.isExternalSVG) {
      this.icon = `/custom-icons/${this.icon}`;
      this.loadSvg();
    } else {
      this.iconClassName = this.icon.startsWith('pi') ? this.icon : `pi pi-${this.icon}`;
    }
  }

  loadSvg() {
    if (!this.icon) {
      return;
    }

    fetch(this.icon)
      .then(res => {
        if (!res.ok) {
          throw new Error(`Failed to load SVG: ${res.status}`);
        }
        return res.text();
      })
      .then(data => {
        // we need to load it this way because we need a way to stylize the svg with css
        const parser = new DOMParser();
        const svg = parser.parseFromString(data, 'image/svg+xml').querySelector('svg');
        if (svg) {
          this.svgWrapper.nativeElement.appendChild(svg);
        } else {
          throw new Error(`Failed to parse SVG`);
        }
      }).catch(error => {
      console.error('Failed to load custom icon: ', error);
      this.svgWrapper.nativeElement.innerHTML = `<svg width="24" height="24">
        <rect width="24" height="24" fill="#ccc"/>
        <text x="12" y="16" text-anchor="middle" font-size="12" fill="#c00">?</text>
      </svg>`;
    });
  }
}
