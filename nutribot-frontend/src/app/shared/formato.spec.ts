import { formatearDuracion } from './formato';

describe('formatearDuracion (DEF-12)', () => {
  it('menos de una hora en minutos', () => {
    expect(formatearDuracion(0)).toBe('0 min');
    expect(formatearDuracion(45)).toBe('45 min');
  });

  it('menos de un dia en horas y minutos', () => {
    expect(formatearDuracion(200)).toBe('3 h 20 min');
  });

  it('un dia o mas en dias y horas', () => {
    expect(formatearDuracion(673 * 60 + 40)).toBe('28 d 1 h');
    expect(formatearDuracion(1440)).toBe('1 d 0 h');
  });

  it('valores vacios o negativos muestran guion', () => {
    expect(formatearDuracion(null)).toBe('—');
    expect(formatearDuracion(-5)).toBe('—');
  });
});
