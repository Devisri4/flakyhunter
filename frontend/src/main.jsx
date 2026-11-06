import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { RouterProvider } from 'react-router'
import { router } from './routes'
import { isServerWaking } from './api/client'
import './index.css'

// While the free backend wakes up, keep trying every 10 s for a few minutes; other errors get one retry as before.
const queryClient = new QueryClient({
  defaultOptions: {
    queries: {
      retry: (failures, error) => failures < (isServerWaking(error) ? 18 : 1),
      retryDelay: (failures, error) => (isServerWaking(error) ? 10_000 : Math.min(1000 * 2 ** failures, 30_000)),
      refetchOnWindowFocus: false,
    },
  },
})

createRoot(document.getElementById('root')).render(
  <StrictMode>
    <QueryClientProvider client={queryClient}>
      <RouterProvider router={router} />
    </QueryClientProvider>
  </StrictMode>,
)
