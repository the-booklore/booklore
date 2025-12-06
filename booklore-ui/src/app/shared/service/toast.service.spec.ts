import { TestBed } from '@angular/core/testing';
import { ToastService } from './toast.service';
import { MessageService } from 'primeng/api';

describe('ToastService', () => {
  let service: ToastService;
  let messageService: jasmine.SpyObj<MessageService>;

  beforeEach(() => {
    const messageServiceSpy = jasmine.createSpyObj('MessageService', ['add']);

    TestBed.configureTestingModule({
      providers: [
        ToastService,
        { provide: MessageService, useValue: messageServiceSpy }
      ]
    });

    service = TestBed.inject(ToastService);
    messageService = TestBed.inject(MessageService) as jasmine.SpyObj<MessageService>;
  });

  it('should be created', () => {
    expect(service).toBeTruthy();
  });

  describe('showSuccess', () => {
    it('should call messageService.add with success severity', () => {
      service.showSuccess('Success Title', 'Success message');

      expect(messageService.add).toHaveBeenCalledWith({
        severity: 'success',
        summary: 'Success Title',
        detail: 'Success message'
      });
    });

    it('should handle empty strings', () => {
      service.showSuccess('', '');

      expect(messageService.add).toHaveBeenCalledWith({
        severity: 'success',
        summary: '',
        detail: ''
      });
    });
  });

  describe('showError', () => {
    it('should call messageService.add with error severity', () => {
      service.showError('Error Title', 'Error message');

      expect(messageService.add).toHaveBeenCalledWith({
        severity: 'error',
        summary: 'Error Title',
        detail: 'Error message'
      });
    });

    it('should handle long messages', () => {
      const longDetail = 'A'.repeat(500);
      service.showError('Error', longDetail);

      expect(messageService.add).toHaveBeenCalledWith({
        severity: 'error',
        summary: 'Error',
        detail: longDetail
      });
    });
  });

  describe('showInfo', () => {
    it('should call messageService.add with info severity', () => {
      service.showInfo('Info Title', 'Info message');

      expect(messageService.add).toHaveBeenCalledWith({
        severity: 'info',
        summary: 'Info Title',
        detail: 'Info message'
      });
    });
  });

  describe('showWarning', () => {
    it('should call messageService.add with warn severity', () => {
      service.showWarning('Warning Title', 'Warning message');

      expect(messageService.add).toHaveBeenCalledWith({
        severity: 'warn',
        summary: 'Warning Title',
        detail: 'Warning message'
      });
    });
  });

  describe('multiple calls', () => {
    it('should handle multiple sequential calls', () => {
      service.showSuccess('Success', 'Success message');
      service.showError('Error', 'Error message');
      service.showInfo('Info', 'Info message');
      service.showWarning('Warning', 'Warning message');

      expect(messageService.add).toHaveBeenCalledTimes(4);
    });
  });

  describe('special characters', () => {
    it('should handle special characters in messages', () => {
      const specialSummary = 'Test <script>alert("xss")</script>';
      const specialDetail = 'Message with "quotes" and \'apostrophes\'';

      service.showInfo(specialSummary, specialDetail);

      expect(messageService.add).toHaveBeenCalledWith({
        severity: 'info',
        summary: specialSummary,
        detail: specialDetail
      });
    });

    it('should handle unicode characters', () => {
      const unicodeSummary = '成功 🎉';
      const unicodeDetail = '操作已完成 📚';

      service.showSuccess(unicodeSummary, unicodeDetail);

      expect(messageService.add).toHaveBeenCalledWith({
        severity: 'success',
        summary: unicodeSummary,
        detail: unicodeDetail
      });
    });
  });
});
