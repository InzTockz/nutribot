/**
 * Sprint 4 (DEF-12): convierte minutos en un texto facil de leer.
 *  - menos de 1 hora  -> "45 min"
 *  - menos de 1 dia   -> "3 h 20 min"
 *  - 1 dia o mas      -> "28 d 1 h"   (antes salia "673 h 40 min")
 */
export function formatearDuracion(minutos: number | null | undefined): string {
  if (minutos === null || minutos === undefined || isNaN(minutos) || minutos < 0) {
    return '—';
  }
  const total = Math.floor(minutos);
  if (total < 60) {
    return `${total} min`;
  }
  if (total < 1440) {
    return `${Math.floor(total / 60)} h ${total % 60} min`;
  }
  const dias = Math.floor(total / 1440);
  const horas = Math.floor((total % 1440) / 60);
  return `${dias} d ${horas} h`;
}
