/**
 * 文件作用：
 * 提供基于路径读取和写入对象属性的公共工具，
 * 让通用表单和通用表格都能支持 `user.name` 这类嵌套字段声明。
 */

/**
 * 方法效果：
 * 按点分路径安全读取对象中的嵌套属性。
 * 参数：
 * - `source`：待读取的原始对象。
 * - `path`：点分路径，例如 `user.name`。
 * 返回值：
 * - 命中时返回对应属性值；
 * - 路径不存在时返回 `undefined`。
 */
export const getValueByPath = (source: unknown, path: string): unknown => {
  if (!source || !path) {
    return undefined
  }

  return path.split('.').reduce<unknown>((currentValue, currentKey) => {
    if (currentValue === null || currentValue === undefined || typeof currentValue !== 'object') {
      return undefined
    }

    return (currentValue as Record<string, unknown>)[currentKey]
  }, source)
}

/**
 * 方法效果：
 * 按点分路径向对象中写入嵌套属性，不存在的中间层会自动创建。
 * 参数：
 * - `target`：待写入的目标对象。
 * - `path`：点分路径，例如 `user.name`。
 * - `value`：要写入的属性值。
 * 返回值：
 * - 无返回值；副作用是直接修改传入对象。
 */
export const setValueByPath = (target: Record<string, unknown>, path: string, value: unknown) => {
  if (!path) {
    return
  }

  const keys = path.split('.')
  const lastKey = keys.pop()

  if (!lastKey) {
    return
  }

  /**
   * 逐层向下走时，如果中间节点不存在或不是对象，就先补成空对象，
   * 这样表单在更新嵌套字段时不会因为上层缺失而报错。
   */
  const parentContainer = keys.reduce<Record<string, unknown>>((currentContainer, currentKey) => {
    const nextValue = currentContainer[currentKey]

    if (!nextValue || typeof nextValue !== 'object' || Array.isArray(nextValue)) {
      currentContainer[currentKey] = {}
    }

    return currentContainer[currentKey] as Record<string, unknown>
  }, target)

  parentContainer[lastKey] = value
}
