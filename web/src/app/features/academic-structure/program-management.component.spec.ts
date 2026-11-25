import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { provideRouter } from '@angular/router';
import { ProgramManagementComponent } from './program-management.component';
import { AuthService } from '../../core/services/auth.service';

describe('ProgramManagementComponent', () => {
  function make(): ProgramManagementComponent {
    return TestBed.createComponent(ProgramManagementComponent).componentInstance;
  }

  beforeEach(() => {
    // The two child components it imports pull HTTP services + auth into the injector.
    const auth = jasmine.createSpyObj<AuthService>('AuthService', ['currentUser']);
    auth.currentUser.and.returnValue(null);
    TestBed.configureTestingModule({
      imports: [ProgramManagementComponent],
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        provideRouter([]),
        { provide: AuthService, useValue: auth },
      ],
    });
  });

  it('defaults to the reference tab', () => {
    expect(make().tab()).toBe('reference');
  });

  it('selectTab switches the active tab', () => {
    const c = make();
    c.selectTab('years');
    expect(c.tab()).toBe('years');
    c.selectTab('reference');
    expect(c.tab()).toBe('reference');
  });
});
