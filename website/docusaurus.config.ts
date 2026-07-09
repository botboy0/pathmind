// Pathmind docs — planning + codebase documentation, docs-only mode.
// Theme borrowed from the Better Draw handbook.

import {themes as prismThemes} from 'prism-react-renderer';
import type {Config} from '@docusaurus/types';
import type * as Preset from '@docusaurus/preset-classic';

const config: Config = {
  title: 'Pathmind',
  tagline: 'Visual node-based Minecraft automation — Addon API + Lua scripting',
  favicon: 'img/favicon.ico',

  future: {
    v4: true,
  },

  url: 'https://pathmind.example.com',
  baseUrl: '/',

  organizationName: 'botboy0',
  projectName: 'pathmind',

  // Migrated planning docs contain relative links to repo files; don't fail the build on them.
  onBrokenLinks: 'warn',

  markdown: {
    // .md files (migrated GSD planning docs) parse as CommonMark, .mdx as MDX.
    format: 'detect',
    hooks: {
      onBrokenMarkdownLinks: 'warn',
    },
  },

  i18n: {
    defaultLocale: 'en',
    locales: ['en'],
  },

  presets: [
    [
      'classic',
      {
        docs: {
          routeBasePath: '/', // docs-only mode: docs are the site root
          sidebarPath: './sidebars.ts',
        },
        blog: false,
        theme: {
          customCss: './src/css/custom.css',
        },
      } satisfies Preset.Options,
    ],
  ],

  themeConfig: {
    colorMode: {
      defaultMode: 'dark',
      respectPrefersColorScheme: false,
    },
    navbar: {
      title: 'Pathmind',
      items: [
        {
          type: 'docSidebar',
          sidebarId: 'docsSidebar',
          position: 'left',
          label: 'Docs',
        },
      ],
    },
    footer: {
      style: 'dark',
      copyright: `Pathmind — Addon API + Lua Scripting Addon`,
    },
    prism: {
      theme: prismThemes.github,
      darkTheme: prismThemes.dracula,
      additionalLanguages: ['java', 'lua', 'json', 'gradle', 'bash', 'powershell'],
    },
  } satisfies Preset.ThemeConfig,
};

export default config;
