import { createBrowserRouter } from 'react-router'
import { Layout, ProjectGate, RequireAuth } from './components/Layout'
import { LandingPage } from './features/home/LandingPage'
import { LoginPage, SignupPage } from './features/auth/AuthPages'
import { ProjectsPage } from './features/projects/ProjectsPage'
import { ProjectPage } from './features/projects/ProjectPage'
import { SettingsPage } from './features/projects/SettingsPage'
import { RunsPage } from './features/runs/RunsPage'
import { RunDetailPage } from './features/runs/RunDetailPage'
import { SetupPage } from './features/setup/SetupPage'
import { TestsPage } from './features/tests/TestsPage'
import { TestDetailPage } from './features/tests/TestDetailPage'
import { SignaturesPage } from './features/tests/SignaturesPage'

export const router = createBrowserRouter([
  {
    element: <Layout />,
    children: [
      { path: '/', element: <LandingPage /> },
      { path: '/login', element: <LoginPage /> },
      { path: '/signup', element: <SignupPage /> },
      {
        element: <RequireAuth />,
        children: [{ path: '/projects', element: <ProjectsPage /> }],
      },
      {
        // readable without an account for the public demo (/p/demo); other projects ask for a login
        path: '/p/:slug',
        element: <ProjectGate />,
        children: [
          { index: true, element: <ProjectPage /> },
          { path: 'settings', element: <SettingsPage /> },
          { path: 'runs', element: <RunsPage /> },
          { path: 'runs/:id', element: <RunDetailPage /> },
          { path: 'setup', element: <SetupPage /> },
          { path: 'tests', element: <TestsPage /> },
          { path: 'tests/:id', element: <TestDetailPage /> },
          { path: 'signatures', element: <SignaturesPage /> },
        ],
      },
      { path: '*', element: <p className="muted">Page not found.</p> },
    ],
  },
])
