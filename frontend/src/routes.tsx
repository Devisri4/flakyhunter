import { createBrowserRouter } from 'react-router'
import { Layout, RequireAuth } from './components/Layout'
import { LandingPage } from './features/home/LandingPage'
import { LoginPage, SignupPage } from './features/auth/AuthPages'
import { ProjectsPage } from './features/projects/ProjectsPage'
import { ProjectPage } from './features/projects/ProjectPage'
import { SettingsPage } from './features/projects/SettingsPage'

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
        ],
      },
      { path: '*', element: <p className="muted">Page not found.</p> },
    ],
  },
])
