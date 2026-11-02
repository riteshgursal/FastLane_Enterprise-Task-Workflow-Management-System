import { expect, test } from '@playwright/test';

// Requires the backend and MySQL running (docker compose up). Creates a fresh account each run.
test('register, create workspace and project, create a task and move it', async ({ page }) => {
  const email = `e2e-${Date.now()}@example.com`;
  await page.goto('/register');
  await page.getByLabel('Full name').fill('Riya Shah');
  await page.getByLabel('Work email').fill(email);
  await page.getByLabel('Password').fill('correct-horse-battery');
  await page.getByRole('button', { name: 'Create account' }).click();

  await page.getByLabel('Workspace name').fill('Acme Corp');
  await page.getByRole('button', { name: 'Create workspace' }).click();

  await page.getByRole('button', { name: 'New project' }).click();
  await page.getByLabel('Name').fill('Website redesign');
  await page.getByRole('button', { name: 'Create project' }).click();
  await expect(page.getByRole('heading', { name: 'Website redesign' })).toBeVisible();

  await page.getByRole('button', { name: '+ New task' }).first().click();
  await page.getByLabel('Title').fill('Login page redesign');
  await page.getByRole('button', { name: 'Create task' }).click();
  await page.getByRole('link', { name: /Login page redesign/ }).click();

  await page.getByRole('button', { name: 'In progress' }).click();
  await expect(page.getByText('Moved to In progress')).toBeVisible();
});
