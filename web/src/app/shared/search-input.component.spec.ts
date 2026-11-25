import { ComponentFixture, TestBed } from '@angular/core/testing';
import { SearchInputComponent } from './search-input.component';

describe('SearchInputComponent', () => {
  let fixture: ComponentFixture<SearchInputComponent>;
  let component: SearchInputComponent;

  function input(): HTMLInputElement {
    return (fixture.nativeElement as HTMLElement).querySelector('input')!;
  }
  function clearButton(): HTMLButtonElement | null {
    return (fixture.nativeElement as HTMLElement).querySelector('.input-clear');
  }

  beforeEach(async () => {
    await TestBed.configureTestingModule({ imports: [SearchInputComponent] }).compileComponents();
    fixture = TestBed.createComponent(SearchInputComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('renders the placeholder and no clear button when empty', () => {
    component.placeholder = 'Find a user';
    fixture.detectChanges();
    expect(input().placeholder).toBe('Find a user');
    expect(clearButton()).toBeNull();
  });

  it('onInput updates the value, notifies the form model, and emits valueChange', () => {
    const changes: string[] = [];
    const emits: string[] = [];
    component.registerOnChange((v) => changes.push(v));
    component.valueChange.subscribe((v) => emits.push(v));

    component.onInput('ann');

    expect(component.value()).toBe('ann');
    expect(changes).toEqual(['ann']);
    expect(emits).toEqual(['ann']);
  });

  it('shows the clear button once there is a value, and clearing resets to empty', () => {
    component.onInput('ann');
    fixture.detectChanges();
    expect(clearButton()).not.toBeNull();

    const emits: string[] = [];
    component.valueChange.subscribe((v) => emits.push(v));
    component.clear(new MouseEvent('mousedown'));

    expect(component.value()).toBe('');
    expect(emits).toEqual(['']);
    fixture.detectChanges();
    expect(clearButton()).toBeNull();
  });

  it('writeValue (form -> view) reflects into the input, null becoming empty', () => {
    component.writeValue('hello');
    fixture.detectChanges();
    expect(input().value).toBe('hello');

    component.writeValue(null);
    fixture.detectChanges();
    expect(component.value()).toBe('');
  });

  it('the [value] input setter mirrors writeValue', () => {
    component.valueInput = 'preset';
    expect(component.value()).toBe('preset');
    component.valueInput = null;
    expect(component.value()).toBe('');
  });

  it('setDisabledState disables the input and hides the clear button', () => {
    component.onInput('x');
    component.setDisabledState(true);
    fixture.detectChanges();
    expect(input().disabled).toBeTrue();
    expect(clearButton()).toBeNull();
  });
});
