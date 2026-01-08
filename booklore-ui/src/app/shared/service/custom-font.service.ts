import {Injectable, inject} from '@angular/core';
import {HttpClient} from '@angular/common/http';
import {BehaviorSubject, Observable, tap} from 'rxjs';
import {CustomFont} from '../model/custom-font.model';
import {API_CONFIG} from '../../core/config/api-config';
import {AuthService} from './auth.service';

@Injectable({
  providedIn: 'root'
})
export class CustomFontService {
  private apiUrl = `${API_CONFIG.BASE_URL}/api/v1/custom-fonts`;
  private fontsSubject = new BehaviorSubject<CustomFont[]>([]);
  public fonts$ = this.fontsSubject.asObservable();
  private loadedFonts = new Set<string>();
  private authService = inject(AuthService);

  constructor(private http: HttpClient) {}

  uploadFont(file: File, fontName?: string): Observable<CustomFont> {
    const formData = new FormData();
    formData.append('file', file);
    if (fontName) {
      formData.append('fontName', fontName);
    }

    return this.http.post<CustomFont>(`${this.apiUrl}/upload`, formData).pipe(
      tap(font => {
        // Add to cache
        const currentFonts = this.fontsSubject.value;
        this.fontsSubject.next([...currentFonts, font]);
        // Load the font immediately
        this.loadFontFace(font).catch(err => {
          console.error('Failed to load font after upload:', err);
        });
      })
    );
  }

  getUserFonts(): Observable<CustomFont[]> {
    return this.http.get<CustomFont[]>(this.apiUrl).pipe(
      tap(fonts => {
        this.fontsSubject.next(fonts);
      })
    );
  }

  deleteFont(fontId: number): Observable<void> {
    return this.http.delete<void>(`${this.apiUrl}/${fontId}`).pipe(
      tap(() => {
        // Remove from cache
        const currentFonts = this.fontsSubject.value;
        const updatedFonts = currentFonts.filter(f => f.id !== fontId);
        this.fontsSubject.next(updatedFonts);

        // Remove from loaded fonts set and document.fonts
        const deletedFont = currentFonts.find(f => f.id === fontId);
        if (deletedFont) {
          this.removeFontFace(deletedFont.fontName);
          this.loadedFonts.delete(deletedFont.fontName);
        }
      })
    );
  }

  getFontUrl(fontId: number): string {
    return `${this.apiUrl}/${fontId}/file`;
  }

  private getToken(): string | null {
    return this.authService.getOidcAccessToken() || this.authService.getInternalAccessToken();
  }

  public appendToken(url: string): string {
    const token = this.getToken();
    return token ? `${url}${url.includes('?') ? '&' : '?'}token=${token}` : url;
  }

  async loadFontFace(font: CustomFont): Promise<void> {
    if (this.loadedFonts.has(font.fontName)) {
      return;
    }

    try {
      const absoluteFontUrl = this.getFontUrl(font.id);
      const fontUrlWithToken = this.appendToken(absoluteFontUrl);

      const fontFace = new FontFace(
        font.fontName,
        `url(${fontUrlWithToken})`,
        {
          weight: 'normal',
          style: 'normal'
        }
      );

      await fontFace.load();
      document.fonts.add(fontFace);
      this.loadedFonts.add(font.fontName);
    } catch (error) {
      console.error(`Failed to load font ${font.fontName}:`, error);
      throw error;
    }
  }

  async loadAllFonts(fonts: CustomFont[]): Promise<void> {
    const loadPromises = fonts.map(font => this.loadFontFace(font));
    await Promise.allSettled(loadPromises);
  }

  isFontLoaded(fontName: string): boolean {
    return this.loadedFonts.has(fontName);
  }

  private removeFontFace(fontName: string): void {
    for (const font of document.fonts) {
      if (font.family === fontName) {
        document.fonts.delete(font);
      }
    }
  }
}
