import { useQuery } from "@tanstack/react-query";
import {
  Button,
  Typography,
  List,
  ListItem,
  ListItemText,
  CircularProgress,
  Alert,
  Box,
} from "@mui/material";

// Updated fetcher for JSONPlaceholder
const fetchUsers = async () => {
  const res = await fetch("https://jsonplaceholder.typicode.com/users");
  if (!res.ok) throw new Error("Failed to fetch");
  return res.json(); // Returns array directly
};

function App() {
  const { data, isLoading, error } = useQuery({
    queryKey: ["users"], // Changed query key
    queryFn: fetchUsers,
  });

  return (
    <div className="p-8">
      <Typography variant="h3" component="h1" className="mb-6">
        User List {/* Updated title */}
      </Typography>

      {isLoading && (
        <Box display="flex" justifyContent="center" my={4}>
          <CircularProgress />
        </Box>
      )}

      {error && (
        <Alert severity="error" className="mb-4">
          Error: {error.message}
        </Alert>
      )}

      {data && (
        <List
          sx={{ width: "100%", maxWidth: 600, bgcolor: "background.paper" }}
        >
          {/* Map directly over data (no .results) */}
          {data.map((user: any) => (
            <ListItem key={user.id} divider>
              <ListItemText primary={user.name} secondary={user.email} />
            </ListItem>
          ))}
        </List>
      )}

      <Button
        variant="contained"
        disableElevation
        className="mt-6 bg-blue-500 hover:bg-blue-700"
      >
        Refresh Data
      </Button>
    </div>
  );
}

export default App;
