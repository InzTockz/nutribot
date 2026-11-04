/**
 * Sprint 4 (DEF-11): solo se aceptan celulares peruanos.
 * Regla (la misma que valida el backend en PendingQueryService.normalizarCelular):
 *  - se ignoran espacios, guiones, puntos y parentesis
 *  - se acepta el prefijo +51 o 51
 *  - quedan 9 digitos y el primero es 9
 * Devuelve el celular limpio ("987654321") o null si no es valido.
 */
export function normalizarCelular(texto: string | null | undefined): string | null {
  let digitos = (texto ?? '').replace(/[\s\-().]/g, '');
  if (digitos.startsWith('+51')) {
    digitos = digitos.substring(3);
  } else if (digitos.startsWith('51') && digitos.length === 11) {
    digitos = digitos.substring(2);
  }
  return /^9\d{8}$/.test(digitos) ? digitos : null;
}
