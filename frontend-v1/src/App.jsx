import { BrowserRouter } from 'react-router-dom'
import { QueryClientProvider } from '@tanstack/react-query'
import AppRoutes from './routes/AppRoutes'
import InlineValidation from './components/common/InlineValidation'
import { queryClient } from './core/query/queryClient'

function App() {
  return (
    <QueryClientProvider client={queryClient}>
      <BrowserRouter>
        {/* UI-6: inline validation feedback on every form */}
        <InlineValidation />
        <AppRoutes />
      </BrowserRouter>
    </QueryClientProvider>
  )
}

export default App
