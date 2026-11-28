import { expect, Locator, Page } from '@playwright/test';

/**
 * Opens a row's "..." menu and chooses an item from it.
 *
 * The app closes this menu on any scroll, so that it never floats away from its row. A
 * list that has just loaded or been filtered can still be settling when the menu opens,
 * and that late scroll closes it again before the item is clicked. A user would simply
 * open it again, so this does the same: if the item is gone, reopen and retry.
 */
export async function chooseFromRowMenu(
  page: Page,
  row: Locator,
  menuName: string,
  item: { role: 'button' | 'link'; name: string },
): Promise<void> {
  await expect(async () => {
    const menu = page.getByRole('menu');
    if (!(await menu.isVisible())) {
      await row.getByRole('button', { name: menuName }).click();
    }
    await menu.getByRole(item.role, { name: item.name }).click({ timeout: 3_000 });
  }).toPass();
}
