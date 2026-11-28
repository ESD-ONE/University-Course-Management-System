import { expect, Locator, Page } from '@playwright/test';

/**
 * Opens a row's "..." menu and chooses an item from it.
 *
 * The app closes this menu on any scroll, so that it never floats away from its row. A
 * list that has just loaded or been filtered can still be settling when the menu opens,
 * and that late scroll closes it again before the item is clicked. A user would simply
 * open it again, so this does the same.
 */
export async function chooseFromRowMenu(
  page: Page,
  row: Locator,
  menuName: string,
  item: { role: 'button' | 'link'; name: string },
): Promise<void> {
  const trigger = row.getByRole('button', { name: menuName });
  const target = page.getByRole('menu').getByRole(item.role, { name: item.name });

  for (let attempt = 1; ; attempt++) {
    // Open the menu, again if it closed, until the item is there to click.
    await expect(async () => {
      if (!(await target.isVisible())) await trigger.click();
      await expect(target).toBeVisible({ timeout: 1_000 });
    }).toPass();

    try {
      await target.click({ timeout: 10_000 });
      return;
    } catch (error) {
      const itemGone = !(await target.isVisible());
      const rowGone = !(await trigger.isVisible());
      // The click worked and took the page somewhere else (a menu link).
      if (itemGone && rowGone) return;
      // Only a menu that closed under the click is worth another try.
      if (!itemGone || attempt === 3) throw error;
    }
  }
}
