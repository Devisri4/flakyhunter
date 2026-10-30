import { createBrowserRouter } from 'react-router'
import { Layout, RequireAuth } from './components/Layout'
import { LandingPage } from './features/home/LandingPage'
import { LoginPage, SignupPage } from './features/auth/AuthPages'
import { ProjectsPage } from './features/projects/ProjectsPage'
import { ProjectPage } from './features/projects/ProjectPage'
import { SettingsPage } from './features/projects/SettingsPage'
import { RunsPage } from './features/runs/RunsPage'
import { RunDetailPage } from './features/runs/RunDetailPage'
import { SetupPage } from './features/setup/SetupPage'

export const router = createBrowserRouter([
  {
    element: <Layout />,
    children: [
      { path: '/', element: <LandingPage /> },
      { path: '/login', element: <LoginPage /> },
      { path: '/signup', element: <SignupPage /> },
      {
        element: <RequireAuth />,
        children: [
          { path: '/projects', element: <ProjectsPage /> },
          { path: '/p/:slug', element: <ProjectPage /> },
          { path: '/p/:slug/settings', element: <SettingsPage /> },
          { path: '/p/:slug/runs', element: <RunsPage /> },
          { path: '/p/:slug/runs/:id', element: <RunDetailPage /> },
          { path: '/p/:slug/setup', element: <SetupPage /> },
        ],
      },
      { path: '*', element: <p className="muted">Page not found.</p> },
    ],
  },
])
