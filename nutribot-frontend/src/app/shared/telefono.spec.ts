import { normalizarCelular } from './telefono';

describe('normalizarCelular (DEF-11)', () => {
  it('acepta 9 digitos que empiezan con 9', () => {
    expect(normalizarCelular('987654321')).toBe('987654321');
  });

  it('acepta prefijo +51 o 51, espacios y guiones', () => {
    expect(normalizarCelular('+51 987 654 321')).toBe('987654321');
    expect(normalizarCelular('51987654321')).toBe('987654321');
    expect(normalizarCelular('987-654-321')).toBe('987654321');
  });

  it('rechaza texto, fijos y longitudes incorrectas', () => {
    expect(normalizarCelular('Gonzales')).toBeNull();
    expect(normalizarCelular('014567890')).toBeNull();
    expect(normalizarCelular('98765432')).toBeNull();
    expect(normalizarCelular('9876543210')).toBeNull();
    expect(normalizarCelular('')).toBeNull();
    expect(normalizarCelular(null)).toBeNull();
  });
});
