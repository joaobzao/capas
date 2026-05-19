export default {
  async scheduled(event, env, ctx) {
    // Cloudflare cron triggers run in UTC. We want 07:00 Europe/Lisbon
    // year-round, which is 07:00 UTC in winter (WET) and 06:00 UTC in
    // summer (WEST). Fire only when the current Lisbon hour is 7.
    const lisbonHour = parseInt(
      new Date().toLocaleString('en-US', {
        timeZone: 'Europe/Lisbon',
        hour: 'numeric',
        hour12: false,
      }),
      10,
    );

    if (lisbonHour !== 7) {
      console.log(`Skipping — Lisbon hour is ${lisbonHour}, not 7.`);
      return;
    }

    const response = await fetch(
      'https://api.github.com/repos/joaobzao/capas/actions/workflows/update-capas.yml/dispatches',
      {
        method: 'POST',
        headers: {
          Authorization: `Bearer ${env.GITHUB_PAT}`,
          Accept: 'application/vnd.github+json',
          'X-GitHub-Api-Version': '2022-11-28',
          'User-Agent': 'capas-trigger-worker',
        },
        body: JSON.stringify({
          ref: 'main',
          inputs: { send_notification: 'true' },
        }),
      },
    );

    if (!response.ok) {
      const text = await response.text();
      throw new Error(`GitHub API ${response.status}: ${text}`);
    }
    console.log('Workflow dispatch triggered successfully.');
  },
};
