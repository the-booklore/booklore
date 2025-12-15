import {Injectable} from '@angular/core';

@Injectable({providedIn: 'root'})
export class FaviconService {
  private svgTemplate = (color: string) => `
	<svg
	  class="logo-icon"
	  viewBox="0 0 126 126"
	  xmlns="http://www.w3.org/2000/svg"
	  aria-hidden="true"
	  focusable="false"
	>
	  <path
	    d="M102.13,3.17c0-1.75-1.42-3.17-3.17-3.17H26.92C16.49,0,7.96,8.13,7.96,18.06v89.87c0,9.94,8.53,18.06,18.96,18.06h72.17c10.43,0,18.96-8.13,18.96-18.06V24.57c0-1.75-1.42-3.17-3.17-3.17h-41.41c-4.25,0-7.7,3.45-7.7,7.7v26.67c0,1.39-1.67,2.11-2.68,1.15l-10.79-10.28c-.61-.58-1.57-.58-2.19,0l-10.79,10.28c-1.01.96-2.68.25-2.68-1.15V21.4h-8.29c-5.96,0-10.83-3.39-10.83-7.53s4.87-7.53,10.83-7.53h70.6c1.75,0,3.17-1.42,3.17-3.17h.01Z"
		fill="${color}"
	  />
	  <path
	    d="M60.18,15.13v29.96l-5.01-3-.23-.13c-2.36-1.29-5.46-1.25-7.77.13l-5.01,3V15.14"
		fill="white"
	  />
	  <rect
	    x="23.99"
		y="10.13"
		width="74.63"
		height="6.69"
		rx="3.34"
		ry="3.34"
		fill="white"
	  />
	</svg>
  `;

  updateFavicon(color: string) {
    const svg = this.svgTemplate(color);
    const blob = new Blob([svg], {type: 'image/svg+xml'});
    const url = URL.createObjectURL(blob);

    let favicon = document.querySelector("link[rel*='icon']") as HTMLLinkElement;
    if (!favicon) {
      favicon = document.createElement('link');
      favicon.rel = 'icon';
      document.head.appendChild(favicon);
    }

    favicon.type = 'image/svg+xml';
    favicon.href = url;
  }
}
