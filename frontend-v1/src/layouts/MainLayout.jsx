import { useRef } from 'react';
import { Outlet, useLocation } from 'react-router-dom';
import Navbar from '../components/Navbar';
import Footer from '../components/Footer';
import HelpButton from '../components/common/HelpButton';
import useScrollReveal from '../core/hooks/useScrollReveal';

const MainLayout = () => {
  const location = useLocation();
  const pageRef = useRef(null);
  useScrollReveal(pageRef, location.pathname);

  return (
    <div className="app-shell flex flex-col min-h-screen">
      <Navbar />
      <main className="flex-1">
        <div ref={pageRef} key={location.pathname} className="route-enter min-h-full">
          <Outlet />
        </div>
      </main>
      <HelpButton />
      <Footer />
    </div>
  );
};

export default MainLayout;
