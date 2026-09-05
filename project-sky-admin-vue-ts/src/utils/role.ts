export const ADMIN_ROLE = 'ADMIN'
export const EMPLOYEE_ROLE = 'EMPLOYEE'

export function normalizeRole(role: string | undefined | null) {
  return role === ADMIN_ROLE ? ADMIN_ROLE : EMPLOYEE_ROLE
}

export function hasRole(roles: string[] = [], role: string) {
  return roles.includes(role)
}

export function isAdminRole(roles: string[] = []) {
  return hasRole(roles, ADMIN_ROLE)
}
