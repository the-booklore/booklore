import { Component, ElementRef, Input, OnInit, ViewChild } from "@angular/core";

@Component({
  selector: 'app-icon-component',
  imports: [],
  templateUrl: './icon-component.html',
  styleUrl: './icon-component.scss'
})

export class IconComponent  implements OnInit {
  @Input() icon: null | undefined | string = '';
  @ViewChild('svgWrapper', { static: false }) svgWrapper!: ElementRef;

  isExternalSVG = false;

  ngOnInit(): void {
    if (!this.icon) {
      return;
    }

    this.isExternalSVG = this.icon?.endsWith('.svg');

    if (this.isExternalSVG) {
      this.icon = `/custom-icons/${this.icon}`;
      this.loadSvg();
    } else {
      this.icon = `pi pi-${this.icon}`;
    }
  }

  loadSvg(){
    if (!this.icon) {
      return;
    }

    fetch(this.icon)
      .then(res => res.text())
      .then(data => {
        // we need to load it this way because we need a way to stylize the svg with css
        const parser = new DOMParser();
        const svg = parser.parseFromString(data, 'image/svg+xml').querySelector('svg');
        this.svgWrapper.nativeElement.appendChild(svg);
    })
  }

}
