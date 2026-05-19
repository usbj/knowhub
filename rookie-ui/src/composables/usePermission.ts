import { computed } from 'vue'
import { storeToRefs } from 'pinia'
import { useLayoutNavigationStore } from '@/stores/navigation'

const normalizePermissionInput = (permissionKey?: string | string[] | readonly string[]) => {
  if (!permissionKey) {
    return []
  }

  const permissionKeys: readonly string[] =
    typeof permissionKey === 'string' ? [permissionKey] : permissionKey

  return permissionKeys
    .map((item) => item.trim())
    .filter(Boolean)
}

export const usePermission = () => {
  const navigationStore = useLayoutNavigationStore()
  const { buttonPermissionKeys } = storeToRefs(navigationStore)

  const hasPermission = (permissionKey?: string | string[] | readonly string[]) => {
    const requiredKeys = normalizePermissionInput(permissionKey)

    if (requiredKeys.length === 0) {
      return true
    }

    return requiredKeys.some((key) => buttonPermissionKeys.value.has(key))
  }

  return {
    buttonPermissionKeys: computed(() => buttonPermissionKeys.value),
    hasPermission,
  }
}
