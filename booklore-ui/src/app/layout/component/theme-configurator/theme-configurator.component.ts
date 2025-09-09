import {CommonModule} from '@angular/common';
import {Component, computed, effect, inject} from '@angular/core';
import {FormsModule} from '@angular/forms';
import Aura from '@primeng/themes/aura';
import {ButtonModule} from 'primeng/button';
import {RadioButtonModule} from 'primeng/radiobutton';
import {ToggleSwitchModule} from 'primeng/toggleswitch';
import {InputTextModule} from 'primeng/inputtext';
import {SliderModule, SliderSlideEndEvent} from 'primeng/slider';
import {AppConfigService} from '../../../core/service/app-config.service';
import {FaviconService} from './favicon-service';
import {DialogService, DynamicDialogRef} from 'primeng/dynamicdialog';
import {UploadDialogComponent} from './upload-dialog/upload-dialog.component';
import {UrlHelperService} from '../../../utilities/service/url-helper.service';
import {BackgroundUploadService} from './background-upload.service';
import {debounceTime, Subject} from 'rxjs';

type ColorPalette = Record<string, string>;

interface Palette {
  name: string;
  palette: ColorPalette;
}

@Component({
  selector: 'app-theme-configurator',
  standalone: true,
  templateUrl: './theme-configurator.component.html',
  styleUrls: ['./theme-configurator.component.scss'],
  host: {
    class: 'config-panel hidden'
  },
  imports: [
    CommonModule,
    FormsModule,
    ButtonModule,
    RadioButtonModule,
    ToggleSwitchModule,
    InputTextModule,
    SliderModule
  ],
  providers: [DialogService]
})
export class ThemeConfiguratorComponent {
  readonly configService = inject(AppConfigService);
  readonly faviconService = inject(FaviconService);
  readonly urlHelper = inject(UrlHelperService);
  private readonly backgroundUploadService = inject(BackgroundUploadService);

  readonly surfaces = this.configService.surfaces;

  readonly selectedPrimaryColor = computed(() => this.configService.appState().primary);
  readonly selectedSurfaceColor = computed(() => this.configService.appState().surface);

  readonly faviconColor = computed(() => {
    const name = this.selectedPrimaryColor() ?? AppConfigService.DEFAULT_PRIMARY_COLOR;
    const presetPalette = (Aura.primitive ?? {}) as Record<string, ColorPalette>;
    const colorPalette = presetPalette[name];
    return colorPalette?.[500] ?? name;
  });

  private readonly _faviconSyncEffect = effect(() => {
    this.faviconService.updateFavicon(this.faviconColor());
  });

  readonly primaryColors = computed<Palette[]>(() => {
    const presetPalette = (Aura.primitive ?? {}) as Record<string, ColorPalette>;
    const colors = ['emerald', 'green', 'lime', 'orange', 'amber', 'yellow', 'teal', 'cyan', 'sky', 'blue', 'indigo', 'violet', 'purple', 'fuchsia', 'pink', 'rose'];
    return [{name: 'noir', palette: {}}].concat(
      colors.map(name => ({
        name,
        palette: presetPalette[name] ?? {}
      }))
    );
  });

  get backgroundVisible(): boolean {
    return this.configService.appState().showBackground ?? true;
  }

  set backgroundVisible(value: boolean) {
    this.configService.appState.update(state => ({
      ...state,
      showBackground: value
    }));
  }

  get backgroundBlurValue(): number {
    return this.configService.appState().backgroundBlur ?? AppConfigService.DEFAULT_BACKGROUND_BLUR;
  }

  set backgroundBlurValue(value: number) {
    this.configService.updateBackgroundBlur(value);
  }

  get surfaceAlphaValue(): number {
    return this.configService.appState().surfaceAlpha ?? AppConfigService.DEFAULT_SURFACE_ALPHA;
  }

  set surfaceAlphaValue(value: number) {
    this.configService.updateSurfaceAlpha(value);
  }

  private readonly dialogService = inject(DialogService);
  private dialogRef: DynamicDialogRef | undefined;

  private surfaceAlphaSubject = new Subject<number>();

  constructor() {
    this.surfaceAlphaSubject.pipe(
      debounceTime(100)
    ).subscribe(value => {
      this.configService.updateSurfaceAlpha(value);
    });
  }

  openUploadDialog() {
    this.dialogRef = this.dialogService.open(UploadDialogComponent, {
      header: 'Upload or Paste Image URL',
      width: '450px',
      modal: true,
      closable: true,
      data: {}
    });

    this.dialogRef.onClose.subscribe((result) => {
      if (result) {
        if (result.success || result.uploaded || result.url || result.imageUrl) {
          this.configService.refreshBackgroundImage();
        }
      }
    });
  }

  updateColors(event: Event, type: 'primary' | 'surface', color: { name: string; palette?: ColorPalette }) {
    this.configService.appState.update((state) => ({
      ...state,
      [type]: color.name
    }));
    event.stopPropagation();
  }

  updateBackgroundBlur(event: SliderSlideEndEvent): void {
    this.configService.appState.update(state => ({
      ...state,
      backgroundBlur: Number(event.value)
    }));
  }

  updateSurfaceAlpha(event: SliderSlideEndEvent): void {
    this.surfaceAlphaSubject.next(Number(event.value));
  }

  resetBackground() {
    this.backgroundUploadService.resetToDefault().subscribe({
      next: () => {
        this.configService.refreshBackgroundImage();
      },
      error: (err) => {
        console.error('Failed to reset background:', err);
      }
    });
  }
}
