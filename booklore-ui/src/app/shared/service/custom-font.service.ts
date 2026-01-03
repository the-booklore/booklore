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
  private loadedFonts = new Set<string>(); // Track loaded font identifiers
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
    // Check if already loaded
    if (this.loadedFonts.has(font.fontName)) {
      return;
    }

    try {
      // Use getFontUrl to get the full absolute URL and append authentication token
      const absoluteFontUrl = this.getFontUrl(font.id);
      const fontUrlWithToken = this.appendToken(absoluteFontUrl);

      const fontFace = new FontFace(
        font.fontName,  // Use actual font name like "Bookerly"
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

  clearCache(): void {
    // Remove all loaded fonts from document.fonts
    this.loadedFonts.forEach(fontName => {
      this.removeFontFace(fontName);
    });

    this.fontsSubject.next([]);
    this.loadedFonts.clear();
  }

  /**
   * Remove a FontFace from document.fonts by font family name
   * @param fontName The font family name to remove
   */
  private removeFontFace(fontName: string): void {
    // Iterate through document.fonts and remove matching FontFace
    const fontsToRemove: FontFace[] = [];
    document.fonts.forEach((font: FontFace) => {
      if (font.family === fontName) {
        fontsToRemove.push(font);
      }
    });

    fontsToRemove.forEach(font => {
      document.fonts.delete(font);
    });
  }
}
