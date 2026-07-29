import React from 'react';
import { useNavigate } from 'react-router-dom';
import { 
  Box, 
  Typography, 
  Button,
  Card,
  CardContent
} from '@mui/material';
import Lock from '@mui/icons-material/Lock';
import { useSelector } from 'react-redux';
import RoutePath from '../core/constants/routes.constant';

const Unauthorized = () => {
  const navigate = useNavigate();
  const { user } = useSelector(state => state.auth);

  return (
    <Box sx={{ 
      display: 'flex', 
      justifyContent: 'center', 
      alignItems: 'center', 
      minHeight: '70vh',
      p: 3
    }}>
      <Card sx={{ maxWidth: 500, width: '100%', textAlign: 'center' }}>
        <CardContent>
          <Lock sx={{ fontSize: 80, color: 'error.main', mb: 2 }} />
          <Typography variant="h4" gutterBottom>
            Access Denied
          </Typography>
          <Typography variant="body1" paragraph>
            You don't have permission to access this page.
          </Typography>
          <Typography variant="body1" paragraph>
            Your current role is: <strong>{user?.role || 'Unknown'}</strong>
          </Typography>
          <Box sx={{ mt: 3 }}>
            <Button 
              variant="contained" 
              color="primary"
              onClick={() => navigate(RoutePath.PUBLIC_HOME)}
              sx={{ mr: 2 }}
            >
              Go to Home
            </Button>
            <Button 
              variant="outlined" 
              onClick={() => navigate(-1)}
            >
              Go Back
            </Button>
          </Box>
        </CardContent>
      </Card>
    </Box>
  );
};

export default Unauthorized;